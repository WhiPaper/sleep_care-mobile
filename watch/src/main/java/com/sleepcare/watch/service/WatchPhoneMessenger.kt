package com.sleepcare.watch.service

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

// 워치에서 휴대폰으로 Data Layer 메시지를 보내는 추상 인터페이스입니다.
interface WatchPhoneMessenger {
    suspend fun send(path: String, payload: ByteArray): Boolean
}

// 현재 연결된 모든 휴대폰 노드에 메시지를 전송합니다.
class WearablePhoneMessenger(context: Context) : WatchPhoneMessenger {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    override suspend fun send(path: String, payload: ByteArray): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) return false

        // 노드가 여러 개일 수 있으므로 하나라도 성공하면 전달 성공으로 봅니다.
        var delivered = false
        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(node.id, path, payload).await()
                delivered = true
            }
        }
        return delivered
    }
}

// 테스트나 SDK 미연결 환경에서 사용할 수 있는 빈 전송 구현입니다.
class NoOpWatchPhoneMessenger : WatchPhoneMessenger {
    override suspend fun send(path: String, payload: ByteArray): Boolean = false
}

// 메시지 전송 구현 교체 지점입니다.
object WatchPhoneMessengerFactory {
    fun create(context: Context): WatchPhoneMessenger = WearablePhoneMessenger(context)
}
