package com.sleepcare.watch.service

import com.sleepcare.watch.contracts.WatchHeartRateSample
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// 워치에서 최근 심박 샘플을 잠시 들고 있다가 backfill 요청이 오면 다시 꺼내 주는 메모리 버퍼입니다.
class WatchSampleBuffer(
    private val retentionMinutes: Long = 10L,
) {
    private val samples = ArrayDeque<WatchHeartRateSample>()

    fun append(sample: WatchHeartRateSample) {
        samples.addLast(sample)
        // 워치 메모리에 계속 쌓이지 않도록 새 샘플 시각 기준으로 오래된 값을 제거합니다.
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
