package com.sleepcare.mobile.data.repository

import com.sleepcare.mobile.domain.WatchCursor
import com.sleepcare.watch.contracts.WatchCursorCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchCursorCalculatorTest {
    @Test
    fun `advance moves highest contiguous ack when samples are continuous`() {
        val updated = WatchCursorCalculator.advance(
            sessionId = "session-1",
            current = WatchCursor(sessionId = "session-1", highestContiguousSampleSeq = 2L),
            receivedSampleSeqs = listOf(3L, 4L, 5L),
        )

        assertEquals(5L, updated.highestContiguousSampleSeq)
        assertNull(updated.pendingBackfillFrom)
    }

    @Test
    fun `advance requests backfill when there is a gap after current ack`() {
        val updated = WatchCursorCalculator.advance(
            sessionId = "session-1",
            current = WatchCursor(sessionId = "session-1", highestContiguousSampleSeq = 2L),
            receivedSampleSeqs = listOf(4L, 5L, 6L),
        )

        assertEquals(2L, updated.highestContiguousSampleSeq)
        assertEquals(3L, updated.pendingBackfillFrom)
    }
}
