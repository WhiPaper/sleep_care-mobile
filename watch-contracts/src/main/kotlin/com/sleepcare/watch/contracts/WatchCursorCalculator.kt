package com.sleepcare.watch.contracts

import java.time.LocalDateTime

// 워치 샘플 sequence의 누락 여부를 계산하는 순수 유틸입니다.
object WatchCursorCalculator {
    // 현재까지 받은 sampleSeq 목록으로 가장 긴 연속 구간과 backfill 시작점을 계산합니다.
    fun advance(
        sessionId: String,
        current: WatchCursor,
        receivedSampleSeqs: Collection<Long>,
    ): WatchCursor {
        var highest = current.highestContiguousSampleSeq
        val ordered = receivedSampleSeqs.distinct().sorted()

        // highest + 1부터 끊기지 않고 이어지는 구간만 ACK 대상으로 올립니다.
        for (sampleSeq in ordered) {
            when {
                sampleSeq == highest + 1L -> highest = sampleSeq
                sampleSeq > highest + 1L -> break
            }
        }

        // 연속 구간 뒤에 더 큰 샘플이 있으면 그 사이가 비어 있으므로 backfill을 요청합니다.
        val pendingBackfillFrom = ordered.firstOrNull { it > highest + 1L }?.let { highest + 1L }
        return current.copy(
            sessionId = sessionId,
            highestContiguousSampleSeq = highest,
            pendingBackfillFrom = pendingBackfillFrom,
        )
    }

    // ACK 전송 시각만 덧붙이는 작은 헬퍼입니다.
    fun markAckSent(cursor: WatchCursor, ackSentAt: LocalDateTime): WatchCursor =
        cursor.copy(lastAckSentAt = ackSentAt)
}
