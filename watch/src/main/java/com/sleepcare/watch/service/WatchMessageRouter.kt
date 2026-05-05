package com.sleepcare.watch.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.sleepcare.watch.contracts.WatchPaths
import com.sleepcare.watch.contracts.WatchProtocolCodec
import com.sleepcare.watch.runtime.WatchSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Data Layer 메시지를 manifest listener와 화면 live listener가 같은 방식으로 처리하도록 모은 라우터입니다.
// 일부 Wear OS 환경에서는 백그라운드 listener 기동이 늦거나 막힐 수 있어, 화면이 열린 동안 직접 listener도 함께 둡니다.
object WatchMessageRouter {
    private const val TAG = "SleepCareWatch"
    private const val DuplicateWindowMs = 2_000L
    private const val MaxRecentMessageKeys = 16

    private val routerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentMessageKeys = LinkedHashMap<String, Long>()

    fun route(
        context: Context,
        messageEvent: MessageEvent,
        source: String,
    ) {
        val envelope = WatchProtocolCodec.decodeEnvelope(messageEvent.data)
        val sessionId = envelope?.sessionId
        val key = "${messageEvent.path}:${sessionId ?: "none"}:${messageEvent.data.contentHashCode()}"
        if (isRecentDuplicate(key)) {
            Log.d(TAG, "duplicate message ignored source=$source, path=${messageEvent.path}, sid=${sessionId ?: "none"}")
            return
        }

        WatchSessionStore.recordIncomingMessage(
            path = messageEvent.path,
            sessionId = sessionId,
            parsed = envelope != null,
        )
        Log.d(
            TAG,
            "message received source=$source, path=${messageEvent.path}, sid=${sessionId ?: "none"}, parsed=${envelope != null}, bytes=${messageEvent.data.size}",
        )
        // 메시지 path는 명령 종류이고, data는 WatchProtocolCodec JSON payload입니다.
        when (messageEvent.path) {
            WatchPaths.SessionStart -> forwardToTrackingService(context, "start", sessionId, messageEvent.data) {
                WatchSensorTrackingService.startFromPhone(context, messageEvent.data)
            }
            WatchPaths.SessionStop -> forwardToTrackingService(context, "stop", sessionId, messageEvent.data) {
                WatchSensorTrackingService.stopFromPhone(context, messageEvent.data)
            }
            WatchPaths.FlushPolicyUpdate -> forwardToTrackingService(context, "flush_policy", sessionId, messageEvent.data) {
                WatchSensorTrackingService.updateFlushPolicyFromPhone(context, messageEvent.data)
            }
            WatchPaths.BackfillRequest -> forwardToTrackingService(context, "backfill", sessionId, messageEvent.data) {
                WatchSensorTrackingService.requestBackfillFromPhone(context, messageEvent.data)
            }
            WatchPaths.AlertVibrate -> forwardToTrackingService(context, "vibrate", sessionId, messageEvent.data) {
                WatchSensorTrackingService.triggerAlertFromPhone(context, messageEvent.data)
            }
            WatchPaths.HrAck -> {
                val cursor = WatchProtocolCodec.decodeHeartRateAck(messageEvent.data)
                val ackLabel = "ack received sample=${cursor?.highestContiguousSampleSeq ?: "unknown"}"
                WatchSessionStore.recordCommandHandled(ackLabel, cursor?.sessionId ?: sessionId)
                Log.d(TAG, "$ackLabel sid=${cursor?.sessionId ?: sessionId ?: "none"}")
            }
            else -> {
                WatchSessionStore.recordCommandError(
                    label = "unknown path ${messageEvent.path}",
                    sessionId = sessionId,
                    detail = "no listener branch",
                )
                Log.d(TAG, "unknown path ignored path=${messageEvent.path}, sid=${sessionId ?: "none"}")
            }
        }
    }

    private fun forwardToTrackingService(
        context: Context,
        label: String,
        sessionId: String?,
        rawMessage: ByteArray,
        block: () -> Unit,
    ) {
        runCatching {
            block()
            WatchSessionStore.recordCommandHandled("$label service requested", sessionId)
            Log.d(TAG, "$label service requested sid=${sessionId ?: "none"}")
        }.onFailure { throwable ->
            val safeSessionId = sessionId ?: WatchProtocolCodec.decodeEnvelope(rawMessage)?.sessionId ?: "unknown-session"
            val detail = throwable.message ?: throwable::class.java.simpleName
            WatchSessionStore.recordCommandError("$label service request failed", safeSessionId, detail)
            Log.d(TAG, "$label service request failed sid=$safeSessionId", throwable)
            if (label == "start") {
                // 서비스가 뜨기도 전에 막히면 내부 error 경로가 실행되지 않으므로 listener가 직접 회신합니다.
                routerScope.launch {
                    WatchPhoneMessengerFactory.create(context).send(
                        WatchPaths.SessionError,
                        WatchSessionIntents.buildSessionErrorPayload(
                            sessionId = safeSessionId,
                            code = "foreground_service_launch_failed",
                            message = detail,
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    private fun isRecentDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentMessageKeys) {
            val previous = recentMessageKeys[key]
            recentMessageKeys[key] = now
            val staleKeys = recentMessageKeys
                .filterValues { now - it > DuplicateWindowMs }
                .keys
                .toList()
            staleKeys.forEach { recentMessageKeys.remove(it) }
            while (recentMessageKeys.size > MaxRecentMessageKeys) {
                val oldestKey = recentMessageKeys.keys.firstOrNull() ?: break
                recentMessageKeys.remove(oldestKey)
            }
            return previous != null && now - previous <= DuplicateWindowMs
        }
    }
}
