package com.sleepcare.watch.service

import android.content.Context
import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateSample
import com.sleepcare.watch.contracts.WatchSessionConfig

// 센서 백엔드 시작 결과입니다. 실패 시 휴대폰에 session.error로 전달됩니다.
data class WatchBackendStartResult(
    val started: Boolean,
    val message: String,
)

// 실제 Samsung Health Sensor SDK 또는 테스트용 구현이 맞춰야 하는 인터페이스입니다.
interface WatchSensorBackend {
    suspend fun start(
        config: WatchSessionConfig,
        onSample: (WatchHeartRateSample) -> Unit,
    ): WatchBackendStartResult

    suspend fun updateFlushPolicy(flushPolicy: WatchFlushPolicy)
    suspend fun stop()
}

// 현재 저장소에서는 센서 SDK 연결 전이므로 UI/통신 흐름만 살리는 no-op 백엔드를 씁니다.
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

// 나중에 실제 센서 SDK 구현으로 교체할 단일 생성 지점입니다.
object WatchSensorBackendFactory {
    fun create(context: Context): WatchSensorBackend = NoOpWatchSensorBackend()
}
