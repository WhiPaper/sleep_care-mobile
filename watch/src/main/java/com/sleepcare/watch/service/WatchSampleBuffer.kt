package com.sleepcare.watch.service

import com.sleepcare.watch.contracts.WatchHeartRateSample
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class WatchSampleBuffer(
    private val retentionMinutes: Long = 10L,
) {
    private val samples = ArrayDeque<WatchHeartRateSample>()

    fun append(sample: WatchHeartRateSample) {
        samples.addLast(sample)
        prune(sample.receivedAt)
    }

    fun appendAll(incoming: Collection<WatchHeartRateSample>) {
        incoming.forEach(::append)
    }

    fun fromSequence(sessionId: String, fromSampleSeq: Long): List<WatchHeartRateSample> =
        samples.filter { it.sessionId == sessionId && it.sampleSeq >= fromSampleSeq }

    fun snapshot(sessionId: String): List<WatchHeartRateSample> =
        samples.filter { it.sessionId == sessionId }

    fun clear(sessionId: String) {
        samples.removeAll { it.sessionId == sessionId }
    }

    private fun prune(now: LocalDateTime) {
        val cutoff = now.minus(retentionMinutes, ChronoUnit.MINUTES)
        samples.removeAll { it.receivedAt.isBefore(cutoff) }
    }
}
