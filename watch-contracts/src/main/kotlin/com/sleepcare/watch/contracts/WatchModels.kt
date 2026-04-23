package com.sleepcare.watch.contracts

import org.json.JSONObject
import java.time.LocalDateTime

data class WatchFlushPolicy(
    val normalSec: Int = 15,
    val suspectSec: Int = 5,
    val alertSec: Int = 2,
)

data class WatchSessionConfig(
    val sessionId: String,
    val studyMode: String = "focus",
    val flushPolicy: WatchFlushPolicy = WatchFlushPolicy(),
    val hrRequired: Boolean = true,
    val watchVibrationEnabled: Boolean = true,
)

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

data class WatchHeartRateBatch(
    val sessionId: String,
    val messageSequence: Long,
    val deliveryMode: String,
    val samples: List<WatchHeartRateSample>,
)

data class WatchCursor(
    val sessionId: String,
    val highestContiguousSampleSeq: Long = 0L,
    val pendingBackfillFrom: Long? = null,
    val lastAckSentAt: LocalDateTime? = null,
)

sealed interface WatchSessionEvent {
    val sessionId: String
    val sequence: Long
}

data class WatchSessionReady(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val trackerMode: String = "foreground_service",
    val sensorBackend: String = "placeholder",
    val bufferWindowSec: Int = 600,
) : WatchSessionEvent

data class WatchSessionError(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val code: String,
    val detailMessage: String,
    val recoverable: Boolean,
) : WatchSessionEvent

data class WatchSessionClosed(
    override val sessionId: String,
    override val sequence: Long = 0L,
    val reason: String,
    val finalSampleSeq: Long? = null,
) : WatchSessionEvent

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
