package com.sleepcare.mobile.data.repository

import com.sleepcare.mobile.data.local.SleepCareDatabase
import com.sleepcare.mobile.data.local.toDomain
import com.sleepcare.mobile.data.local.toEntity
import com.sleepcare.mobile.domain.WatchCursor
import com.sleepcare.mobile.domain.WatchHeartRateBatch
import com.sleepcare.mobile.domain.WatchHeartRateSample
import com.sleepcare.watch.contracts.WatchCursorCalculator
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

// 워치에서 온 한 배치 중 새 샘플과 갱신된 ACK 커서를 함께 반환합니다.
data class WatchRelayBatchResult(
    val newSamples: List<WatchHeartRateSample>,
    val cursor: WatchCursor,
)

// 워치 심박 샘플을 로컬 DB에 먼저 안전하게 저장하고 Raspberry Pi 전달 상태를 추적합니다.
// Pi가 잠깐 끊겨도 pending 샘플을 다시 보낼 수 있게 하는 중간 릴레이 저장소입니다.
@Singleton
class WatchRelayStore @Inject constructor(
    database: SleepCareDatabase,
) {
    private val sampleDao = database.watchHeartRateSampleDao()
    private val cursorDao = database.watchCursorDao()

    suspend fun recordIncomingBatch(batch: WatchHeartRateBatch): WatchRelayBatchResult {
        val sampleSeqs = batch.samples.map { it.sampleSeq }
        // 이미 저장한 sampleSeq는 중복 저장/전달하지 않습니다.
        val existingSampleSeqs = if (sampleSeqs.isEmpty()) {
            emptyList()
        } else {
            sampleDao.getExistingSampleSeqs(batch.sessionId, sampleSeqs)
        }.toSet()
        val newSamples = batch.samples.filterNot { it.sampleSeq in existingSampleSeqs }
        if (newSamples.isNotEmpty()) {
            sampleDao.upsertAll(newSamples.map { it.toEntity() })
        }

        val currentCursor = cursorDao.getBySessionId(batch.sessionId)?.toDomain()
            ?: WatchCursor(sessionId = batch.sessionId)
        // 현재 ACK 이후 받은 샘플 전체를 보고 가장 긴 연속 구간을 다시 계산합니다.
        val receivedSampleSeqs = sampleDao.getSampleSeqsAfter(
            sessionId = batch.sessionId,
            afterSeq = currentCursor.highestContiguousSampleSeq,
        )
        val updatedCursor = WatchCursorCalculator.advance(
            sessionId = batch.sessionId,
            current = currentCursor,
            receivedSampleSeqs = receivedSampleSeqs,
        )
        cursorDao.upsert(updatedCursor.toEntity())

        return WatchRelayBatchResult(
            newSamples = newSamples,
            cursor = updatedCursor,
        )
    }

    suspend fun markForwarded(sessionId: String, sampleSeqs: Collection<Long>) {
        if (sampleSeqs.isEmpty()) return
        sampleDao.updateRelayState(
            sessionId = sessionId,
            sampleSeqs = sampleSeqs.toList(),
            relayState = RELAY_STATE_FORWARDED,
        )
    }

    suspend fun getPendingSamples(sessionId: String): List<WatchHeartRateSample> =
        sampleDao.getByRelayState(sessionId, RELAY_STATE_PENDING).map { it.toDomain() }

    suspend fun getPendingSessionIds(): List<String> = sampleDao.getSessionIdsByRelayState(RELAY_STATE_PENDING)

    suspend fun touchCursorAck(cursor: WatchCursor): WatchCursor {
        // ACK를 보낸 시각을 남기면 이후 재시도/디버깅 기준점으로 쓸 수 있습니다.
        val updated = cursor.copy(lastAckSentAt = LocalDateTime.now())
        cursorDao.upsert(updated.toEntity())
        return updated
    }

    companion object {
        private const val RELAY_STATE_PENDING = "pending"
        private const val RELAY_STATE_FORWARDED = "forwarded"
    }
}
