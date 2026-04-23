package com.sleepcare.watch.service

import android.content.Context
import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateSample
import com.sleepcare.watch.contracts.WatchSessionConfig

data class WatchBackendStartResult(
    val started: Boolean,
    val message: String,
)

interface WatchSensorBackend {
    suspend fun start(
        config: WatchSessionConfig,
        onSample: (WatchHeartRateSample) -> Unit,
    ): WatchBackendStartResult

    suspend fun updateFlushPolicy(flushPolicy: WatchFlushPolicy)
    suspend fun stop()
}

class NoOpWatchSensorBackend : WatchSensorBackend {
    override suspend fun start(
        config: WatchSessionConfig,
        onSample: (WatchHeartRateSample) -> Unit,
    ): WatchBackendStartResult = WatchBackendStartResult(
        started = true,
        message = "Samsung Health Sensor SDK not attached yet; no-op backend is active.",
    )

    override suspend fun updateFlushPolicy(flushPolicy: WatchFlushPolicy) = Unit

    override suspend fun stop() = Unit
}

object WatchSensorBackendFactory {
    fun create(context: Context): WatchSensorBackend = NoOpWatchSensorBackend()
}
