package com.sleepcare.mobile.data.repository

import com.sleepcare.mobile.domain.ConnectionStatus
import com.sleepcare.mobile.domain.ConnectedDeviceState
import com.sleepcare.mobile.domain.DeviceType
import com.sleepcare.mobile.domain.WatchCommandTargetPolicy
import com.sleepcare.mobile.domain.WatchCursor
import com.sleepcare.mobile.domain.WatchDebugState
import com.sleepcare.mobile.domain.WatchFlushPolicy
import com.sleepcare.mobile.domain.WatchHeartRateBatch
import com.sleepcare.mobile.domain.WatchHeartRateSample
import com.sleepcare.mobile.domain.WatchSessionClosed
import com.sleepcare.mobile.domain.WatchSessionConfig
import com.sleepcare.mobile.domain.WatchSessionDataSource
import com.sleepcare.mobile.domain.WatchSessionEvent
import com.sleepcare.mobile.domain.WatchSessionReady
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDebugRepositoryTest {
    @Test
    fun `debug start sends watch start only with debug config`() = runTest {
        val fake = FakeWatchSessionDataSource()
        val repository = WatchDebugRepositoryImpl(fake)

        repository.startTestSession()
        val state = repository.currentState()

        assertTrue(state.sessionId!!.startsWith("watch-debug-"))
        assertEquals(1, fake.startedConfigs.size)
        assertEquals(state.sessionId, fake.startedConfigs.single().sessionId)
        assertEquals("debug-watch", fake.startedConfigs.single().studyMode)
        assertTrue(fake.startedConfigs.single().hrRequired)
        assertTrue(fake.startedConfigs.single().watchVibrationEnabled)
        assertEquals(1, fake.refreshConnectionCalls)
        assertEquals(listOf(WatchCommandTargetPolicy.DebugAllowPairedFallback), fake.targetPolicies)
        assertEquals(0, fake.stopSessionIds.size)
    }

    @Test
    fun `debug commands use current debug session and latest sample cursor`() = runTest {
        val fake = FakeWatchSessionDataSource()
        val repository = WatchDebugRepositoryImpl(fake)

        repository.startTestSession()
        val sessionId = repository.currentState().sessionId!!

        fake.sessionEvents.emit(WatchSessionReady(sessionId = sessionId, sensorBackend = "fake-sensor"))
        fake.heartRateBatches.emit(
            WatchHeartRateBatch(
                sessionId = sessionId,
                messageSequence = 11L,
                deliveryMode = "batch",
                samples = listOf(sample(sessionId, sampleSeq = 7L)),
            )
        )
        eventually {
            val state = repository.currentState()
            assertEquals(7L, state.latestSampleSeq)
            assertNotNull(state.lastSessionEvent)
        }

        repository.sendFlushPolicy()
        repository.sendVibrationAlert()
        repository.sendAck()
        repository.requestBackfill()
        repository.stopTestSession()

        assertEquals(sessionId, fake.flushPolicies.single().first)
        assertEquals(WatchFlushPolicy(normalSec = 15, suspectSec = 5, alertSec = 2), fake.flushPolicies.single().second)
        assertEquals(Triple(sessionId, 2, "200,100,200"), fake.vibrationRequests.single())
        assertEquals(WatchCursor(sessionId = sessionId, highestContiguousSampleSeq = 7L), fake.ackCursors.single().copy(lastAckSentAt = null))
        assertEquals(sessionId to 5L, fake.backfillRequests.single())
        assertEquals(sessionId, fake.stopSessionIds.single())
        assertEquals(6, fake.targetPolicies.size)
        assertTrue(fake.targetPolicies.all { it == WatchCommandTargetPolicy.DebugAllowPairedFallback })
    }

    @Test
    fun `debug state reflects closed event for current session`() = runTest {
        val fake = FakeWatchSessionDataSource()
        val repository = WatchDebugRepositoryImpl(fake)

        repository.startTestSession()
        val sessionId = repository.currentState().sessionId!!
        fake.sessionEvents.emit(WatchSessionClosed(sessionId = sessionId, reason = "debug_stop", finalSampleSeq = 3L))

        eventually {
            assertEquals(
                "session.closed · debug_stop · final sample 3",
                repository.currentState().lastSessionEvent,
            )
        }
    }

    private fun sample(sessionId: String, sampleSeq: Long): WatchHeartRateSample =
        WatchHeartRateSample(
            sessionId = sessionId,
            messageSequence = sampleSeq,
            sampleSeq = sampleSeq,
            sensorTimestampMs = 1_000L,
            bpm = 72,
            hrStatus = 1,
            ibiMs = listOf(820),
            ibiStatus = listOf(0),
            deliveryMode = "batch",
            receivedAt = LocalDateTime.now(),
        )

    private suspend fun eventually(assertion: () -> Unit) {
        withTimeout(2_000) {
            while (true) {
                try {
                    assertion()
                    return@withTimeout
                } catch (_: AssertionError) {
                    delay(20)
                }
            }
        }
    }
}

private fun WatchDebugRepositoryImpl.currentState(): WatchDebugState =
    (observeDebugState() as StateFlow<WatchDebugState>).value

private class FakeWatchSessionDataSource : WatchSessionDataSource {
    private val connectionState = MutableStateFlow(
        ConnectedDeviceState(
            deviceType = DeviceType.Smartwatch,
            deviceName = "Fake Watch",
            status = ConnectionStatus.Connected,
        )
    )
    val heartRateBatches = MutableSharedFlow<WatchHeartRateBatch>(replay = 1)
    val sessionEvents = MutableSharedFlow<WatchSessionEvent>(replay = 1)
    val startedConfigs = mutableListOf<WatchSessionConfig>()
    val stopSessionIds = mutableListOf<String>()
    val ackCursors = mutableListOf<WatchCursor>()
    val backfillRequests = mutableListOf<Pair<String, Long>>()
    val flushPolicies = mutableListOf<Pair<String, WatchFlushPolicy>>()
    val vibrationRequests = mutableListOf<Triple<String, Int, String>>()
    val targetPolicies = mutableListOf<WatchCommandTargetPolicy>()
    var refreshConnectionCalls = 0

    override fun observeConnectionState(): Flow<ConnectedDeviceState> = connectionState

    override fun observeHeartRateBatches(): Flow<WatchHeartRateBatch> = heartRateBatches

    override fun observeSessionEvents(): Flow<WatchSessionEvent> = sessionEvents

    override suspend fun refreshConnection(): Boolean {
        refreshConnectionCalls += 1
        return true
    }

    override suspend fun startSession(
        config: WatchSessionConfig,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        startedConfigs += config
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun stopSession(
        sessionId: String,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        stopSessionIds += sessionId
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun acknowledgeCursor(
        cursor: WatchCursor,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        ackCursors += cursor
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun requestBackfill(
        sessionId: String,
        fromSampleSeq: Long,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        backfillRequests += sessionId to fromSampleSeq
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun updateFlushPolicy(
        sessionId: String,
        flushPolicy: WatchFlushPolicy,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        flushPolicies += sessionId to flushPolicy
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun sendVibrationAlert(
        sessionId: String,
        level: Int,
        pattern: String,
        targetPolicy: WatchCommandTargetPolicy,
    ): Boolean {
        vibrationRequests += Triple(sessionId, level, pattern)
        targetPolicies += targetPolicy
        return true
    }

    override suspend fun disconnect() = Unit
}
