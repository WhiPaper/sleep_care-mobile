package com.sleepcare.mobile.data.source

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.sleepcare.mobile.domain.ConnectionStatus
import com.sleepcare.mobile.domain.ConnectedDeviceState
import com.sleepcare.mobile.domain.DeviceType
import com.sleepcare.mobile.domain.WatchCursor
import com.sleepcare.mobile.domain.WatchFlushPolicy
import com.sleepcare.mobile.domain.WatchHeartRateBatch
import com.sleepcare.mobile.domain.WatchSessionClosed
import com.sleepcare.mobile.domain.WatchSessionConfig
import com.sleepcare.mobile.domain.WatchSessionDataSource
import com.sleepcare.mobile.domain.WatchSessionError
import com.sleepcare.mobile.domain.WatchSessionEvent
import com.sleepcare.mobile.domain.WatchSessionReady
import com.sleepcare.watch.contracts.WatchPaths
import com.sleepcare.watch.contracts.WatchProtocolCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Wear OS Data Layer를 통해 모바일 앱과 Galaxy Watch 사이의 세션 제어/심박 메시지를 연결합니다.
// 수신 메시지는 Flow로 노출하고, 송신은 WatchPaths + WatchProtocolCodec 계약을 그대로 사용합니다.
@Singleton
class GalaxyWatchSessionDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatchSessionDataSource, MessageClient.OnMessageReceivedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionState = MutableStateFlow(
        ConnectedDeviceState(
            deviceType = DeviceType.Smartwatch,
            deviceName = "Galaxy Watch",
            status = ConnectionStatus.Disconnected,
            details = "Galaxy Watch 연결을 확인하세요.",
        )
    )
    private val heartRateBatches = MutableSharedFlow<WatchHeartRateBatch>(extraBufferCapacity = 32)
    private val sessionEvents = MutableSharedFlow<WatchSessionEvent>(replay = 1, extraBufferCapacity = 16)
    private var currentNode: Node? = null
    private var listenerRegistered = false

    init {
        scope.launch {
            ensureListenerRegistered()
            refreshConnection()
        }
    }

    override fun observeConnectionState(): Flow<ConnectedDeviceState> = connectionState.asStateFlow()

    override fun observeHeartRateBatches(): Flow<WatchHeartRateBatch> = heartRateBatches.asSharedFlow()

    override fun observeSessionEvents(): Flow<WatchSessionEvent> = sessionEvents.asSharedFlow()

    override suspend fun refreshConnection(): Boolean {
        ensureListenerRegistered()
        // 가까운 노드를 우선 선택하면 실제 착용 중인 워치가 먼저 연결됩니다.
        val node = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
                .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.displayName })
                .firstOrNull()
        }.getOrNull()
        currentNode = node
        connectionState.value = if (node == null) {
            ConnectedDeviceState(
                deviceType = DeviceType.Smartwatch,
                deviceName = "Galaxy Watch",
                status = ConnectionStatus.Disconnected,
                details = "연결된 Galaxy Watch를 찾지 못했습니다.",
            )
        } else {
            ConnectedDeviceState(
                deviceType = DeviceType.Smartwatch,
                deviceName = node.displayName.ifBlank { "Galaxy Watch" },
                status = ConnectionStatus.Connected,
                details = if (node.isNearby) "Wear OS Data Layer 연결됨" else "원격 노드로 연결됨",
                lastSeenAt = LocalDateTime.now(),
            )
        }
        return node != null
    }

    override suspend fun startSession(config: WatchSessionConfig): Boolean =
        sendMessage(WatchPaths.Start, WatchProtocolCodec.buildStartPayload(config))

    override suspend fun stopSession(sessionId: String): Boolean =
        sendMessage(WatchPaths.Stop, WatchProtocolCodec.buildStopPayload(sessionId))

    override suspend fun acknowledgeCursor(cursor: WatchCursor): Boolean =
        sendMessage(WatchPaths.Ack, WatchProtocolCodec.buildAckPayload(cursor))

    override suspend fun requestBackfill(sessionId: String, fromSampleSeq: Long): Boolean =
        sendMessage(WatchPaths.Backfill, WatchProtocolCodec.buildBackfillPayload(sessionId, fromSampleSeq))

    override suspend fun updateFlushPolicy(sessionId: String, flushPolicy: WatchFlushPolicy): Boolean =
        sendMessage(WatchPaths.FlushPolicy, WatchProtocolCodec.buildFlushPolicyPayload(sessionId, flushPolicy))

    override suspend fun sendVibrationAlert(sessionId: String, level: Int, pattern: String): Boolean =
        sendMessage(WatchPaths.Vibrate, WatchProtocolCodec.buildVibrationPayload(sessionId, level, pattern))

    override suspend fun disconnect() {
        currentNode = null
        connectionState.value = connectionState.value.copy(
            status = ConnectionStatus.Disconnected,
            details = "Galaxy Watch 연결을 다시 확인하세요.",
        )
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        connectionState.value = connectionState.value.copy(
            status = ConnectionStatus.Connected,
            details = "최근 워치 메시지 수신됨 · ${messageEvent.path}",
            lastSeenAt = LocalDateTime.now(),
        )
        // 세션 이벤트와 심박 배치는 같은 MessageClient로 들어오므로 path로 구분합니다.
        WatchProtocolCodec.parseSessionEvent(messageEvent.path, messageEvent.data)?.let { event ->
            sessionEvents.tryEmit(event)
            return
        }
        val batch = WatchProtocolCodec.parseHeartRateBatch(messageEvent.path, messageEvent.data) ?: return
        heartRateBatches.tryEmit(batch)
    }

    private suspend fun ensureListenerRegistered() {
        if (listenerRegistered) return
        runCatching {
            Wearable.getMessageClient(context).addListener(this).await()
            listenerRegistered = true
        }.onFailure { throwable ->
            connectionState.value = connectionState.value.copy(
                status = ConnectionStatus.Failed,
                details = throwable.message ?: "워치 메시지 리스너 등록에 실패했습니다.",
            )
        }
    }

    private suspend fun sendMessage(path: String, payload: ByteArray): Boolean {
        ensureListenerRegistered()
        // 현재 노드가 없으면 한 번 더 검색해 사용자가 방금 워치를 연결한 경우도 처리합니다.
        val node = currentNode ?: if (refreshConnection()) currentNode else null
        if (node == null) return false
        return runCatching {
            Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
            connectionState.value = connectionState.value.copy(
                status = ConnectionStatus.Connected,
                deviceName = node.displayName.ifBlank { "Galaxy Watch" },
                details = "Wear OS Data Layer 연결됨",
                lastSeenAt = LocalDateTime.now(),
            )
            true
        }.getOrElse { throwable ->
            connectionState.value = connectionState.value.copy(
                status = ConnectionStatus.Failed,
                details = throwable.message ?: "Galaxy Watch 메시지 전송에 실패했습니다.",
            )
            false
        }
    }
}
