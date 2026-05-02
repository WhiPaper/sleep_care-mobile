package com.sleepcare.watch.contracts

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// 모바일 앱과 워치 앱 사이의 JSON payload 인코딩/디코딩을 담당합니다.
// path는 WatchPaths, body 구조는 이 Codec이 책임져 양쪽 구현을 단순하게 유지합니다.
object WatchProtocolCodec {
    // 외부에서 쓰기 쉬운 build* 함수들은 내부 encode* 함수의 의미 있는 별칭입니다.
    fun buildStartPayload(config: WatchSessionConfig): ByteArray = encodeSessionStart(config)

    fun buildStopPayload(sessionId: String): ByteArray = encodeSessionStop(sessionId)

    fun buildFlushPolicyPayload(sessionId: String, flushPolicy: WatchFlushPolicy): ByteArray =
        encodeFlushPolicyUpdate(sessionId, flushPolicy)

    fun buildBackfillPayload(sessionId: String, fromSampleSeq: Long): ByteArray =
        encodeBackfillRequest(sessionId, fromSampleSeq)

    fun buildHeartRateBatchPayload(batch: WatchHeartRateBatch): ByteArray = encodeHeartRateBatch(batch)

    fun buildAckPayload(cursor: WatchCursor): ByteArray = encodeHeartRateAck(cursor)

    fun buildSessionReadyPayload(
        sessionId: String,
        trackerMode: String = "foreground_service",
        sequence: Long = 0L,
        sensorBackend: String = "placeholder",
        bufferWindowSec: Int = 600,
    ): ByteArray =
        encode(
            WatchEnvelope(
                type = "session.ready",
                sessionId = sessionId,
                sequence = sequence,
                source = "watch",
                ackRequired = false,
                body = JSONObject()
                    .put("tracking_state", trackerMode)
                    .put("sensor_backend", sensorBackend)
                    .put("buffer_window_sec", bufferWindowSec),
            )
        )

    fun buildSessionErrorPayload(
        sessionId: String,
        code: String,
        message: String,
        recoverable: Boolean,
        sequence: Long = 0L,
    ): ByteArray =
        encode(
            WatchEnvelope(
                type = "session.error",
                sessionId = sessionId,
                sequence = sequence,
                source = "watch",
                ackRequired = false,
                body = JSONObject()
                    .put("code", code)
                    .put("message", message)
                    .put("recoverable", recoverable),
            )
        )

    fun buildSessionClosedPayload(
        sessionId: String,
        reason: String,
        sequence: Long = 0L,
        finalSampleSeq: Long? = null,
    ): ByteArray =
        encode(
            WatchEnvelope(
                type = "session.closed",
                sessionId = sessionId,
                sequence = sequence,
                source = "watch",
                ackRequired = false,
                body = JSONObject()
                    .put("reason", reason)
                    .put("final_sample_seq", finalSampleSeq ?: JSONObject.NULL),
            )
        )

    fun buildVibrationPayload(sessionId: String, level: Int, pattern: String): ByteArray =
        encodeVibrationAlert(sessionId, level, pattern)

    fun encodeSessionStart(config: WatchSessionConfig): ByteArray =
        encode(
            WatchEnvelope(
                type = "session.start",
                sessionId = config.sessionId,
                source = "phone",
                ackRequired = true,
                body = JSONObject()
                    .put("study_mode", config.studyMode)
                    .put(
                        "flush_policy",
                        JSONObject()
                            .put("normal_sec", config.flushPolicy.normalSec)
                            .put("suspect_sec", config.flushPolicy.suspectSec)
                            .put("alert_sec", config.flushPolicy.alertSec),
                    )
                    .put("hr_required", config.hrRequired)
                    .put("watch_vibration_enabled", config.watchVibrationEnabled),
            )
        )

    fun encodeSessionStop(sessionId: String): ByteArray =
        encode(
            WatchEnvelope(
                type = "session.stop",
                sessionId = sessionId,
                source = "phone",
                ackRequired = true,
            )
        )

    fun encodeFlushPolicyUpdate(sessionId: String, flushPolicy: WatchFlushPolicy): ByteArray =
        encode(
            WatchEnvelope(
                type = "flush_policy.update",
                sessionId = sessionId,
                source = "phone",
                ackRequired = true,
                body = JSONObject()
                    .put("normal_sec", flushPolicy.normalSec)
                    .put("suspect_sec", flushPolicy.suspectSec)
                    .put("alert_sec", flushPolicy.alertSec),
            )
        )

    fun encodeBackfillRequest(sessionId: String, fromSampleSeq: Long): ByteArray =
        encode(
            WatchEnvelope(
                type = "backfill.request",
                sessionId = sessionId,
                source = "phone",
                ackRequired = true,
                body = JSONObject().put("from_sample_seq", fromSampleSeq),
            )
        )

    fun encodeHeartRateSample(sample: WatchHeartRateSample): ByteArray =
        encode(
            WatchEnvelope(
                type = "hr.live",
                sessionId = sample.sessionId,
                sequence = sample.messageSequence,
                source = "watch",
                ackRequired = true,
                body = sample.toJson(),
            )
        )

    fun encodeHeartRateBatch(batch: WatchHeartRateBatch): ByteArray =
        encode(
            WatchEnvelope(
                type = "hr.batch",
                sessionId = batch.sessionId,
                sequence = batch.messageSequence,
                source = "watch",
                ackRequired = true,
                body = JSONObject()
                    .put("delivery_mode", batch.deliveryMode)
                    .put("samples", JSONArray(batch.samples.map { it.toJson() })),
            )
        )

    fun encodeHeartRateAck(cursor: WatchCursor): ByteArray =
        encode(
            WatchEnvelope(
                type = "hr.ack",
                sessionId = cursor.sessionId,
                source = "phone",
                ackRequired = false,
                body = JSONObject()
                    .put("ack_sample_seq", cursor.highestContiguousSampleSeq)
                    .put("pending_backfill_from", cursor.pendingBackfillFrom ?: JSONObject.NULL),
            )
        )

    fun encodeSessionReady(
        sessionId: String,
        readyAtMs: Long = System.currentTimeMillis(),
        sensorBackend: String = "placeholder",
        bufferWindowSec: Int = 600,
    ): ByteArray = buildSessionReadyPayload(
        sessionId = sessionId,
        trackerMode = "active",
        sequence = 0L,
        sensorBackend = sensorBackend,
        bufferWindowSec = bufferWindowSec,
    )

    fun encodeSessionError(
        sessionId: String,
        code: String,
        message: String,
        recoverable: Boolean,
    ): ByteArray = buildSessionErrorPayload(
        sessionId = sessionId,
        code = code,
        message = message,
        recoverable = recoverable,
    )

    fun encodeSessionClosed(
        sessionId: String,
        reason: String,
        finalSampleSeq: Long? = null,
    ): ByteArray = buildSessionClosedPayload(
        sessionId = sessionId,
        reason = reason,
        finalSampleSeq = finalSampleSeq,
    )

    fun encodeVibrationAlert(sessionId: String, level: Int, pattern: String): ByteArray =
        encode(
            WatchEnvelope(
                type = "alert.vibrate",
                sessionId = sessionId,
                source = "phone",
                ackRequired = true,
                body = JSONObject()
                    .put("level", level)
                    .put("pattern", pattern),
            )
        )

    fun decodeEnvelope(raw: ByteArray): WatchEnvelope? = runCatching {
        // 잘못된 메시지가 들어와도 워치/앱 프로세스가 죽지 않도록 null로 흘려보냅니다.
        val root = JSONObject(String(raw, UTF_8))
        WatchEnvelope(
            version = root.optInt("v", 1),
            type = root.optString("t"),
            sessionId = root.optString("sid").takeIf { it.isNotBlank() },
            sequence = root.optLong("seq", 0L),
            source = root.optString("src", "watch"),
            sentAtMs = root.optLong("sent_at_ms", System.currentTimeMillis()),
            ackRequired = root.optBoolean("ack_required", true),
            body = root.optJSONObject("body") ?: JSONObject(),
        )
    }.getOrNull()

    fun decodeSessionConfig(raw: ByteArray): WatchSessionConfig? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        val policy = envelope.body.optJSONObject("flush_policy")
        return WatchSessionConfig(
            sessionId = sessionId,
            studyMode = envelope.body.optString("study_mode", "focus"),
            flushPolicy = WatchFlushPolicy(
                normalSec = policy?.optInt("normal_sec", 15) ?: 15,
                suspectSec = policy?.optInt("suspect_sec", 5) ?: 5,
                alertSec = policy?.optInt("alert_sec", 2) ?: 2,
            ),
            hrRequired = envelope.body.optBoolean("hr_required", true),
            watchVibrationEnabled = envelope.body.optBoolean("watch_vibration_enabled", true),
        )
    }

    fun decodeFlushPolicy(raw: ByteArray): WatchFlushPolicy? {
        val envelope = decodeEnvelope(raw) ?: return null
        val policy = envelope.body.optJSONObject("flush_policy") ?: envelope.body
        return WatchFlushPolicy(
            normalSec = policy.optInt("normal_sec", 15),
            suspectSec = policy.optInt("suspect_sec", 5),
            alertSec = policy.optInt("alert_sec", 2),
        )
    }

    fun decodeBackfillRequest(raw: ByteArray): Long? {
        val envelope = decodeEnvelope(raw) ?: return null
        return envelope.body.optLong("from_sample_seq", -1L).takeIf { it >= 0L }
    }

    fun decodeHeartRateBatch(raw: ByteArray): WatchHeartRateBatch? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        // live 단일 샘플과 batch 배열을 같은 WatchHeartRateBatch 타입으로 정규화합니다.
        val samples = when (envelope.type) {
            "hr.live" -> listOf(envelope.body.toSample(sessionId, envelope.sequence))
            else -> envelope.body.optJSONArray("samples")?.toSampleList(sessionId, envelope.sequence).orEmpty()
        }
        return WatchHeartRateBatch(
            sessionId = sessionId,
            messageSequence = envelope.sequence,
            deliveryMode = envelope.body.optString(
                "delivery_mode",
                if (envelope.type == "hr.live") "live" else "batch",
            ),
            samples = samples,
        )
    }

    fun decodeHeartRateAck(raw: ByteArray): WatchCursor? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        return WatchCursor(
            sessionId = sessionId,
            highestContiguousSampleSeq = envelope.body.optLong("ack_sample_seq", 0L),
            pendingBackfillFrom = envelope.body.optLong("pending_backfill_from", -1L).takeIf { it >= 0L },
        )
    }

    fun decodeVibrationAlert(raw: ByteArray): Pair<Int, String>? {
        val envelope = decodeEnvelope(raw) ?: return null
        return envelope.body.optInt("level", 1) to envelope.body.optString("pattern", "pulse")
    }

    fun parseSessionConfig(raw: ByteArray): WatchSessionConfig? = decodeSessionConfig(raw)

    fun parseAckCursor(raw: ByteArray): WatchCursor? = decodeHeartRateAck(raw)

    fun parseBackfillRequest(raw: ByteArray): Pair<String, Long>? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        val fromSampleSeq = envelope.body.optLong("from_sample_seq", -1L).takeIf { it >= 0L } ?: return null
        return sessionId to fromSampleSeq
    }

    fun parseFlushPolicy(raw: ByteArray): Pair<String, WatchFlushPolicy>? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        val policy = decodeFlushPolicy(raw) ?: return null
        return sessionId to policy
    }

    fun parseVibrationRequest(raw: ByteArray): Triple<String, Int, String>? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        val (level, pattern) = decodeVibrationAlert(raw) ?: return null
        return Triple(sessionId, level, pattern)
    }

    fun parseSessionEvent(path: String, raw: ByteArray): WatchSessionEvent? {
        val envelope = decodeEnvelope(raw) ?: return null
        val sessionId = envelope.sessionId ?: return null
        // 같은 JSON envelope라도 Data Layer path에 따라 세션 이벤트 타입을 확정합니다.
        return when (path) {
            WatchPaths.SessionReady -> WatchSessionReady(
                sessionId = sessionId,
                sequence = envelope.sequence,
                trackerMode = envelope.body.optString("tracking_state", "foreground_service"),
                sensorBackend = envelope.body.optString("sensor_backend", "placeholder"),
                bufferWindowSec = envelope.body.optInt("buffer_window_sec", 600),
            )

            WatchPaths.SessionError -> WatchSessionError(
                sessionId = sessionId,
                sequence = envelope.sequence,
                code = envelope.body.optString("code", "unknown"),
                detailMessage = envelope.body.optString("message", "Unknown watch error"),
                recoverable = envelope.body.optBoolean("recoverable", false),
            )

            WatchPaths.SessionClosed -> WatchSessionClosed(
                sessionId = sessionId,
                sequence = envelope.sequence,
                reason = envelope.body.optString("reason", "unknown"),
                finalSampleSeq = envelope.body.optLong("final_sample_seq", -1L).takeIf { it >= 0L },
            )

            else -> null
        }
    }

    fun parseHeartRateBatch(path: String, raw: ByteArray): WatchHeartRateBatch? {
        if (path != WatchPaths.HrBatch && path != WatchPaths.HrLive) {
            return null
        }
        return decodeHeartRateBatch(raw)
    }

    private fun encode(envelope: WatchEnvelope): ByteArray =
        // JSONObject.NULL을 명시해야 nullable sessionId도 JSON 필드 구조를 유지합니다.
        JSONObject()
            .put("v", envelope.version)
            .put("t", envelope.type)
            .put("sid", envelope.sessionId ?: JSONObject.NULL)
            .put("seq", envelope.sequence)
            .put("src", envelope.source)
            .put("sent_at_ms", envelope.sentAtMs)
            .put("ack_required", envelope.ackRequired)
            .put("body", envelope.body)
            .toString()
            .toByteArray(UTF_8)

    private fun WatchHeartRateSample.toJson(): JSONObject =
        JSONObject()
            .put("session_id", sessionId)
            .put("message_sequence", messageSequence)
            .put("sample_seq", sampleSeq)
            .put("sensor_ts_ms", sensorTimestampMs)
            .put("bpm", bpm)
            .put("hr_status", hrStatus)
            .put("ibi_ms", JSONArray(ibiMs))
            .put("ibi_status", JSONArray(ibiStatus))
            .put("delivery_mode", deliveryMode)
            .put("received_at_ms", receivedAt.toEpochMillis())

    private fun JSONObject.toSample(sessionId: String, messageSequence: Long): WatchHeartRateSample {
        val receivedAt = optLong("received_at_ms", System.currentTimeMillis()).toLocalDateTime()
        return WatchHeartRateSample(
            sessionId = optString("session_id", sessionId),
            messageSequence = optLong("message_sequence", messageSequence),
            sampleSeq = optLong("sample_seq", messageSequence),
            sensorTimestampMs = optLong("sensor_ts_ms", System.currentTimeMillis()),
            bpm = optInt("bpm", 0),
            hrStatus = optInt("hr_status", 0),
            ibiMs = optJSONArray("ibi_ms").toIntList(),
            ibiStatus = optJSONArray("ibi_status").toIntList(),
            deliveryMode = optString("delivery_mode", "live"),
            receivedAt = receivedAt,
        )
    }

    private fun JSONArray.toSampleList(sessionId: String, messageSequence: Long): List<WatchHeartRateSample> {
        val samples = mutableListOf<WatchHeartRateSample>()
        // 일부 항목이 깨져 있어도 정상 샘플은 가능한 한 살려서 전달합니다.
        for (index in 0 until length()) {
            val payload = optJSONObject(index) ?: continue
            samples += payload.toSample(sessionId, messageSequence)
        }
        return samples
    }

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return List(length()) { index -> optInt(index) }
    }
}

private fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

private fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
