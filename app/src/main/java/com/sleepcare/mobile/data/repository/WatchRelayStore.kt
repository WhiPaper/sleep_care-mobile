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

data class WatchRelayBatchResult(
    val newSamples: List<WatchHeartRateSample>,
    val cursor: WatchCursor,
)

@Singleton
class WatchRelayStore @Inject constructor(
    database: SleepCareDatabase,
) {
    private val sampleDao = database.watchHeartRateSampleDao()
    private val cursorDao = database.watchCursorDao()

    suspend fun recordIncomingBatch(batch: WatchHeartRateBatch): WatchRelayBatchResult {
        val sampleSeqs = batch.samples.map { it.sampleSeq }
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
        val updated = cursor.copy(lastAckSentAt = LocalDateTime.now())
        cursorDao.upsert(updated.toEntity())
        return updated
    }

    companion object {
        private const val RELAY_STATE_PENDING = "pending"
        private const val RELAY_STATE_FORWARDED = "forwarded"
    }
}
