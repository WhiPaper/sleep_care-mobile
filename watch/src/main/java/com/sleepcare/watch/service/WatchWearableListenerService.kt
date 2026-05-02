package com.sleepcare.watch.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sleepcare.watch.contracts.WatchPaths
import com.sleepcare.watch.contracts.WatchProtocolCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 휴대폰 앱에서 보낸 Wear OS Data Layer 메시지를 받아 전면 추적 서비스 Intent로 변환합니다.
class WatchWearableListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // 메시지 path는 명령 종류이고, data는 WatchProtocolCodec JSON payload입니다.
        when (messageEvent.path) {
            WatchPaths.SessionStart -> startTrackingServiceOrReport(messageEvent.data)
            WatchPaths.SessionStop -> WatchSensorTrackingService.stopFromPhone(applicationContext, messageEvent.data)
            WatchPaths.FlushPolicyUpdate -> WatchSensorTrackingService.updateFlushPolicyFromPhone(applicationContext, messageEvent.data)
            WatchPaths.BackfillRequest -> WatchSensorTrackingService.requestBackfillFromPhone(applicationContext, messageEvent.data)
            WatchPaths.AlertVibrate -> WatchSensorTrackingService.triggerAlertFromPhone(applicationContext, messageEvent.data)
        }
    }

    private fun startTrackingServiceOrReport(rawMessage: ByteArray) {
        runCatching {
            WatchSensorTrackingService.startFromPhone(applicationContext, rawMessage)
        }.onFailure { throwable ->
            val sessionId = WatchProtocolCodec.decodeEnvelope(rawMessage)?.sessionId ?: "unknown-session"
            // 서비스가 뜨기도 전에 막히면 내부 error 경로가 실행되지 않으므로 listener가 직접 회신합니다.
            serviceScope.launch {
                WatchPhoneMessengerFactory.create(applicationContext).send(
                    WatchPaths.SessionError,
                    WatchSessionIntents.buildSessionErrorPayload(
                        sessionId = sessionId,
                        code = "foreground_service_launch_failed",
                        message = throwable.message ?: "Unable to launch watch foreground service.",
                        recoverable = true,
                    ),
                )
            }
        }
    }
}
