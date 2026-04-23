package com.sleepcare.watch.contracts

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchCursorCalculatorTest {
    @Test
    fun advancesContiguousSequenceAndMarksGap() {
        val current = WatchCursor(sessionId = "session-1", highestContiguousSampleSeq = 4L)

        val updated = WatchCursorCalculator.advance(
            sessionId = "session-1",
            current = current,
            receivedSampleSeqs = listOf(5L, 6L, 8L, 7L),
        )

        assertEquals(8L, updated.highestContiguousSampleSeq)
        assertEquals(null, updated.pendingBackfillFrom)
    }

    @Test
    fun marksBackfillWhenGapAppearsAfterContiguousRange() {
        val current = WatchCursor(sessionId = "session-1", highestContiguousSampleSeq = 4L)

        val updated = WatchCursorCalculator.advance(
            sessionId = "session-1",
            current = current,
            receivedSampleSeqs = listOf(5L, 7L, 8L),
        )

        assertEquals(5L, updated.highestContiguousSampleSeq)
        assertEquals(6L, updated.pendingBackfillFrom)
    }
}
