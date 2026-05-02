package com.sleepcare.watch.contracts

import org.json.JSONObject
import java.time.LocalDateTime

// 모바일 앱과 Wear OS 앱이 함께 쓰는 워치 통신 모델입니다.
// 이 모듈에 있는 타입을 공유하면 양쪽 JSON 계약이 어긋나는 일을 줄일 수 있습니다.

// 위험도 단계별로 워치가 심박 샘플을 얼마나 자주 flush할지 정합니다.
data class WatchFlushPolicy(
    val normalSec: Int = 15,
    val suspectSec: Int = 5,
    val alertSec: Int = 2,
)

// 모바일 앱이 워치에 새 추적 세션 시작을 요청할 때 보내는 설정입니다.
data class WatchSessionConfig(
    val sessionId: String,
    val studyMode: String = "focus",
    val flushPolicy: WatchFlushPolicy = WatchFlushPolicy(),
    val hrRequired: Boolean = true,
    val watchVibrationEnabled: Boolean = true,
)

// 워치에서 측정한 심박/IBI 단일 샘플입니다.
data class WatchHeartRateSample(
    val sessionId: String,
    val messageSequence: Long,
    val sampleSeq: Long,
    val sensorTimestampMs: Long,
    val bpm: Int,
    val hrStatus: Int,
    val ibiMs: List<Int>,
    val ibiStatus: List<Int>,
    val deliveryMode: String,
    val receivedAt: LocalDateTime,
)

// 여러 심박 샘플을 한 메시지로 묶어 보내기 위한 배치입니다.
data class WatchHeartRateBatch(
    val sessionId: String,
    val messageSequence: Long,
    val deliveryMode: String,
    val samples: List<WatchHeartRateSample>,
)

// 모바일 앱이 어디까지 연속 샘플을 받았는지 워치에 알려주는 ACK 커서입니다.
data class WatchCursor(
    val sessionId: String,
    val highestContiguousSampleSeq: Long = 0L,
    val pendingBackfillFrom: Long? = null,
    val lastAckSentAt: LocalDateTime? = null,
)

// 워치 세션 상태 이벤트의 공통 부모입니다.
sealed interface WatchSessionEvent {
    val sessionId: String
    val sequence: Long
}

// 워치가 센서 추적 준비를 완료했음을 알리는 이벤트입니다.
data class WatchSessionReady(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val trackerMode: String = "foreground_service",
    val sensorBackend: String = "placeholder",
    val bufferWindowSec: Int = 600,
) : WatchSessionEvent

// 워치 센서/권한/서비스 시작 문제를 모바일 앱에 전달하는 이벤트입니다.
data class WatchSessionError(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val code: String,
    val detailMessage: String,
    val recoverable: Boolean,
) : WatchSessionEvent

// 워치 쪽 추적 세션이 끝났음을 알리는 이벤트입니다.
data class WatchSessionClosed(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val reason: String,
    val finalSampleSeq: Long? = null,
) : WatchSessionEvent

// Data Layer 메시지 내부 JSON 구조의 공통 껍데기입니다.
data class WatchEnvelope(
    val version: Int = 1,
    val type: String,
    val sessionId: String? = null,
    val sequence: Long = 0L,
    val source: String = "watch",
    val sentAtMs: Long = System.currentTimeMillis(),
    val ackRequired: Boolean = true,
    val body: JSONObject = JSONObject(),
)
