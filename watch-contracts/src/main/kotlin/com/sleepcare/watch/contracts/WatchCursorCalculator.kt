package com.sleepcare.watch.contracts

import java.time.LocalDateTime

object WatchCursorCalculator {
    fun advance(
        sessionId: String,
        current: WatchCursor,
        receivedSampleSeqs: Collection<Long>,
    ): WatchCursor {
        var highest = current.highestContiguousSampleSeq
        val ordered = receivedSampleSeqs.distinct().sorted()

        for (sampleSeq in ordered) {
            when {
                sampleSeq == highest + 1L -> highest = sampleSeq
                sampleSeq > highest + 1L -> break
            }
        }

        val pendingBackfillFrom = ordered.firstOrNull { it > highest + 1L }?.let { highest + 1L }
        return current.copy(
            sessionId = sessionId,
            highestContiguousSampleSeq = highest,
            pendingBackfillFrom = pendingBackfillFrom,
        )
    }

    fun markAckSent(cursor: WatchCursor, ackSentAt: LocalDateTime): WatchCursor =
        cursor.copy(lastAckSentAt = ackSentAt)
}
