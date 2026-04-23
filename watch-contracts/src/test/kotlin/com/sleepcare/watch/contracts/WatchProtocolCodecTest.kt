package com.sleepcare.watch.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

class WatchProtocolCodecTest {
    @Test
    fun roundTripsSessionStartPayload() {
        val config = WatchSessionConfig(
            sessionId = "session-1",
            studyMode = "focus",
            flushPolicy = WatchFlushPolicy(normalSec = 12, suspectSec = 6, alertSec = 2),
            hrRequired = true,
            watchVibrationEnabled = false,
        )

        val decoded = WatchProtocolCodec.decodeSessionConfig(WatchProtocolCodec.encodeSessionStart(config))

        assertNotNull(decoded)
        assertEquals(config, decoded)
    }

    @Test
    fun roundTripsHeartRateBatch() {
        val sample = WatchHeartRateSample(
            sessionId = "session-1",
            messageSequence = 3L,
            sampleSeq = 7L,
            sensorTimestampMs = 1000L,
            bpm = 72,
            hrStatus = 1,
            ibiMs = listOf(840, 838),
            ibiStatus = listOf(0, 0),
            deliveryMode = "batch",
            receivedAt = LocalDateTime.of(2026, 4, 23, 10, 15),
        )
        val batch = WatchHeartRateBatch(
            sessionId = sample.sessionId,
            messageSequence = sample.messageSequence,
            deliveryMode = sample.deliveryMode,
            samples = listOf(sample),
        )

        val decoded = WatchProtocolCodec.decodeHeartRateBatch(WatchProtocolCodec.encodeHeartRateBatch(batch))

        assertNotNull(decoded)
        assertEquals(batch.sessionId, decoded?.sessionId)
        assertEquals(batch.samples.first().sampleSeq, decoded?.samples?.first()?.sampleSeq)
        assertEquals(batch.samples.first().bpm, decoded?.samples?.first()?.bpm)
    }

    @Test
    fun parsesReadyErrorAndClosedEvents() {
        val ready = WatchProtocolCodec.parseSessionEvent(
            WatchPaths.SessionReady,
            WatchProtocolCodec.buildSessionReadyPayload(
                sessionId = "session-1",
                trackerMode = "foreground_service",
                sequence = 11L,
                sensorBackend = "samsung-health-sensor-sdk",
                bufferWindowSec = 600,
            ),
        )
        val error = WatchProtocolCodec.parseSessionEvent(
            WatchPaths.SessionError,
            WatchProtocolCodec.buildSessionErrorPayload(
                sessionId = "session-1",
                code = "sensor_denied",
                message = "BODY_SENSORS permission missing",
                recoverable = true,
                sequence = 12L,
            ),
        )
        val closed = WatchProtocolCodec.parseSessionEvent(
            WatchPaths.SessionClosed,
            WatchProtocolCodec.buildSessionClosedPayload(
                sessionId = "session-1",
                reason = "phone_stop",
                sequence = 13L,
                finalSampleSeq = 99L,
            ),
        )

        assertTrue(ready is WatchSessionReady)
        assertTrue(error is WatchSessionError)
        assertTrue(closed is WatchSessionClosed)
        assertEquals("samsung-health-sensor-sdk", (ready as WatchSessionReady).sensorBackend)
        assertEquals("sensor_denied", (error as WatchSessionError).code)
        assertEquals(99L, (closed as WatchSessionClosed).finalSampleSeq)
    }

    @Test
    fun parsesLivePayloadAsSingleSampleBatch() {
        val sample = WatchHeartRateSample(
            sessionId = "session-1",
            messageSequence = 14L,
            sampleSeq = 21L,
            sensorTimestampMs = 2000L,
            bpm = 68,
            hrStatus = 1,
            ibiMs = listOf(882),
            ibiStatus = listOf(0),
            deliveryMode = "live",
            receivedAt = LocalDateTime.of(2026, 4, 23, 11, 5),
        )

        val decoded = WatchProtocolCodec.parseHeartRateBatch(
            WatchPaths.HrLive,
            WatchProtocolCodec.encodeHeartRateSample(sample),
        )

        assertNotNull(decoded)
        assertEquals("live", decoded?.deliveryMode)
        assertEquals(1, decoded?.samples?.size)
        assertEquals(21L, decoded?.samples?.first()?.sampleSeq)
    }
}
