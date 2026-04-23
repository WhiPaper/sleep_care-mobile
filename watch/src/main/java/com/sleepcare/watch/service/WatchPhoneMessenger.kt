package com.sleepcare.watch.service

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

interface WatchPhoneMessenger {
    suspend fun send(path: String, payload: ByteArray): Boolean
}

class WearablePhoneMessenger(context: Context) : WatchPhoneMessenger {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    override suspend fun send(path: String, payload: ByteArray): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) return false

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

class NoOpWatchPhoneMessenger : WatchPhoneMessenger {
    override suspend fun send(path: String, payload: ByteArray): Boolean = false
}

object WatchPhoneMessengerFactory {
    fun create(context: Context): WatchPhoneMessenger = WearablePhoneMessenger(context)
}
