package com.sleepcare.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sleepcare.mobile.domain.DrowsinessEvent
import com.sleepcare.mobile.domain.ExamSchedule
import com.sleepcare.mobile.domain.LastSyncState
import com.sleepcare.mobile.domain.NotificationPreferences
import com.sleepcare.mobile.domain.OnboardingState
import com.sleepcare.mobile.domain.PiSessionSummary
import com.sleepcare.mobile.domain.RecommendationSnapshot
import com.sleepcare.mobile.domain.RecommendationTip
import com.sleepcare.mobile.domain.SleepSession
import com.sleepcare.mobile.domain.StudySessionState
import com.sleepcare.mobile.domain.StudyPlan
import com.sleepcare.mobile.domain.TrustedPiDevice
import com.sleepcare.mobile.domain.UserGoals
import com.sleepcare.mobile.domain.WatchCursor
import com.sleepcare.mobile.domain.WatchHeartRateSample
import com.sleepcare.mobile.data.source.PiPairingCodec
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Room DB와 DataStore를 함께 정의하는 로컬 저장소 파일입니다.
// 구조화된 기록은 Room에, 작은 설정값은 DataStore Preferences에 저장합니다.

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_care_preferences")

// Health Connect에서 읽은 수면 세션을 앱 분석용으로 캐시합니다.
@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val totalMinutes: Int,
    val sleepScore: Int,
    val consistencyScore: Int,
    val latencyMinutes: Int,
    val awakeMinutes: Int,
    val source: String,
)

// Raspberry Pi 알림을 졸음 이벤트로 변환해 저장합니다.
@Entity(tableName = "drowsiness_events")
data class DrowsinessEventEntity(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime,
    val severity: Int,
    val durationMinutes: Int,
    val label: String,
    val deviceId: String,
    val sessionId: String?,
)

// 공부 세션의 시작/종료와 최종 요약을 남기는 테이블입니다.
@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val phase: String,
    val latestRiskState: String?,
    val latestFusedScore: Double?,
    val alertCount: Int,
    val summaryMode: String?,
    val summaryReason: String?,
    val peakFusedScore: Double?,
)

// 워치 심박 샘플은 Pi로 전달 성공 여부까지 함께 추적합니다.
@Entity(tableName = "watch_hr_samples")
data class WatchHeartRateSampleEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val messageSequence: Long,
    val sampleSeq: Long,
    val sensorTimestampMs: Long,
    val bpm: Int,
    val hrStatus: Int,
    val ibiMsSerialized: String,
    val ibiStatusSerialized: String,
    val deliveryMode: String,
    val receivedAt: LocalDateTime,
    val relayState: String,
)

// 워치로 보낸 ACK 커서를 저장해 중복/누락 샘플을 이어서 처리합니다.
@Entity(tableName = "watch_cursors")
data class WatchCursorEntity(
    @PrimaryKey val sessionId: String,
    val highestContiguousSampleSeq: Long,
    val pendingBackfillFrom: Long?,
    val lastAckSentAt: LocalDateTime?,
)

// 사용자가 설정한 기본 공부 계획은 단일 행으로 관리합니다.
@Entity(tableName = "study_plan")
data class StudyPlanEntity(
    @PrimaryKey val id: Int = StudyPlan.DEFAULT_ID,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val focusHours: Int,
    val daysSerialized: String,
    val breakPreferenceMinutes: Int,
    val autoBreakEnabled: Boolean,
)

// 시험 일정은 여러 건을 날짜순으로 보여주고 추천 계산에도 사용합니다.
@Entity(tableName = "exam_schedule")
data class ExamScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String,
    val priority: Int,
    val syncEnabled: Boolean,
)

// 추천 결과는 최신 스냅샷 하나만 보관합니다.
@Entity(tableName = "recommendation_snapshot")
data class RecommendationSnapshotEntity(
    @PrimaryKey val id: Long = 1L,
    val recommendedBedtime: LocalTime,
    val recommendedWakeTime: LocalTime,
    val targetSleepMinutes: Int,
    val reason: String,
    val routineShiftMinutes: Int,
    val tipsSerialized: String,
    val generatedAt: LocalDateTime,
)

// Room이 java.time 타입을 문자열로 저장하고 다시 복원할 수 있게 하는 변환기입니다.
class RoomConverters {
    @TypeConverter
    fun localDateTimeToString(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)
}

// 아래 DAO들은 화면이 직접 SQL을 알 필요 없이 Flow와 suspend 함수로 데이터에 접근하게 해 줍니다.
@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SleepSessionEntity>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clear()
}

@Dao
interface DrowsinessEventDao {
    @Query("SELECT * FROM drowsiness_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DrowsinessEventEntity>>

    @Query("SELECT COUNT(*) FROM drowsiness_events")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DrowsinessEventEntity>)

    @Query("DELETE FROM drowsiness_events")
    suspend fun clear()
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<StudySessionEntity?>

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StudySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StudySessionEntity)

    @Query("DELETE FROM study_sessions")
    suspend fun clear()
}

@Dao
interface WatchHeartRateSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WatchHeartRateSampleEntity>)

    @Query("SELECT sampleSeq FROM watch_hr_samples WHERE sessionId = :sessionId AND sampleSeq IN (:sampleSeqs)")
    suspend fun getExistingSampleSeqs(sessionId: String, sampleSeqs: List<Long>): List<Long>

    @Query("SELECT sampleSeq FROM watch_hr_samples WHERE sessionId = :sessionId AND sampleSeq > :afterSeq ORDER BY sampleSeq ASC")
    suspend fun getSampleSeqsAfter(sessionId: String, afterSeq: Long): List<Long>

    @Query("SELECT * FROM watch_hr_samples WHERE sessionId = :sessionId AND relayState = :relayState ORDER BY sampleSeq ASC")
    suspend fun getByRelayState(sessionId: String, relayState: String): List<WatchHeartRateSampleEntity>

    @Query("SELECT DISTINCT sessionId FROM watch_hr_samples WHERE relayState = :relayState")
    suspend fun getSessionIdsByRelayState(relayState: String): List<String>

    @Query("UPDATE watch_hr_samples SET relayState = :relayState WHERE sessionId = :sessionId AND sampleSeq IN (:sampleSeqs)")
    suspend fun updateRelayState(sessionId: String, sampleSeqs: List<Long>, relayState: String)
}

@Dao
interface WatchCursorDao {
    @Query("SELECT * FROM watch_cursors WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): WatchCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchCursorEntity)
}

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plan WHERE id = :id")
    fun observeById(id: Int = StudyPlan.DEFAULT_ID): Flow<StudyPlanEntity?>

    @Query("SELECT COUNT(*) FROM study_plan")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StudyPlanEntity)

    @Query("DELETE FROM study_plan")
    suspend fun clear()
}

@Dao
interface ExamScheduleDao {
    @Query("SELECT * FROM exam_schedule ORDER BY date ASC, startTime ASC")
    fun observeAll(): Flow<List<ExamScheduleEntity>>

    @Query("SELECT COUNT(*) FROM exam_schedule")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExamScheduleEntity)

    @Query("DELETE FROM exam_schedule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM exam_schedule")
    suspend fun clear()
}

@Dao
interface RecommendationSnapshotDao {
    @Query("SELECT * FROM recommendation_snapshot WHERE id = 1")
    fun observeLatest(): Flow<RecommendationSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecommendationSnapshotEntity)

    @Query("DELETE FROM recommendation_snapshot")
    suspend fun clear()
}

// 앱에서 사용하는 모든 Room 테이블을 묶은 데이터베이스입니다.
@Database(
    entities = [
        SleepSessionEntity::class,
        DrowsinessEventEntity::class,
        StudySessionEntity::class,
        WatchHeartRateSampleEntity::class,
        WatchCursorEntity::class,
        StudyPlanEntity::class,
        ExamScheduleEntity::class,
        RecommendationSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class SleepCareDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun drowsinessEventDao(): DrowsinessEventDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun watchHeartRateSampleDao(): WatchHeartRateSampleDao
    abstract fun watchCursorDao(): WatchCursorDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun examScheduleDao(): ExamScheduleDao
    abstract fun recommendationSnapshotDao(): RecommendationSnapshotDao
}

// DataStore Preferences로 온보딩, 알림 설정, 사용자 목표, 마지막 동기화 시간을 관리합니다.
class PreferencesStore(private val context: Context) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val drowsinessAlerts = booleanPreferencesKey("drowsiness_alerts")
        val sleepReminders = booleanPreferencesKey("sleep_reminders")
        val targetWakeTime = stringPreferencesKey("target_wake_time")
        val preferredBedtime = stringPreferencesKey("preferred_bedtime")
        val sleepSyncedAt = stringPreferencesKey("sleep_synced_at")
        val drowsinessSyncedAt = stringPreferencesKey("drowsiness_synced_at")
        val trustedPiDevice = stringPreferencesKey("trusted_pi_device")
    }

    val onboardingState: Flow<OnboardingState> = context.dataStore.safeData()
        .map { OnboardingState(completed = it[Keys.onboardingCompleted] ?: false) }

    val notificationPreferences: Flow<NotificationPreferences> = context.dataStore.safeData()
        .map {
            NotificationPreferences(
                drowsinessAlertsEnabled = it[Keys.drowsinessAlerts] ?: true,
                sleepRemindersEnabled = it[Keys.sleepReminders] ?: true,
            )
        }

    val userGoals: Flow<UserGoals> = context.dataStore.safeData()
        .map {
            UserGoals(
                targetWakeTime = it[Keys.targetWakeTime]?.let(LocalTime::parse),
                preferredBedtime = it[Keys.preferredBedtime]?.let(LocalTime::parse),
            )
        }

    val lastSyncState: Flow<LastSyncState> = context.dataStore.safeData()
        .map {
            LastSyncState(
                sleepSyncedAt = it[Keys.sleepSyncedAt]?.let(LocalDateTime::parse),
                drowsinessSyncedAt = it[Keys.drowsinessSyncedAt]?.let(LocalDateTime::parse),
            )
        }

    val trustedPiDevice: Flow<TrustedPiDevice?> = context.dataStore.safeData()
        .map { preferences ->
            preferences[Keys.trustedPiDevice]?.let(PiPairingCodec::parseTrustedDevice)
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
        context.dataStore.edit {
            it[Keys.drowsinessAlerts] = preferences.drowsinessAlertsEnabled
            it[Keys.sleepReminders] = preferences.sleepRemindersEnabled
        }
    }

    suspend fun updateUserGoals(goals: UserGoals) {
        context.dataStore.edit {
            if (goals.targetWakeTime == null) {
                it.remove(Keys.targetWakeTime)
            } else {
                it[Keys.targetWakeTime] = goals.targetWakeTime.toString()
            }
            if (goals.preferredBedtime == null) {
                it.remove(Keys.preferredBedtime)
            } else {
                it[Keys.preferredBedtime] = goals.preferredBedtime.toString()
            }
        }
    }

    suspend fun updateLastSyncState(state: LastSyncState) {
        context.dataStore.edit {
            if (state.sleepSyncedAt == null) {
                it.remove(Keys.sleepSyncedAt)
            } else {
                it[Keys.sleepSyncedAt] = state.sleepSyncedAt.toString()
            }
            if (state.drowsinessSyncedAt == null) {
                it.remove(Keys.drowsinessSyncedAt)
            } else {
                it[Keys.drowsinessSyncedAt] = state.drowsinessSyncedAt.toString()
            }
        }
    }

    suspend fun updateTrustedPiDevice(device: TrustedPiDevice) {
        context.dataStore.edit {
            it[Keys.trustedPiDevice] = with(PiPairingCodec) { device.toJson() }
        }
    }

    suspend fun clearTrustedPiDevice() {
        context.dataStore.edit { it.remove(Keys.trustedPiDevice) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

// DataStore 읽기 중 IOException이 나면 앱이 죽지 않도록 빈 Preferences로 대체합니다.
private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { exception ->
    if (exception is IOException) emit(emptyPreferences()) else throw exception
}

// 아래 변환 함수들은 DB Entity와 도메인 모델 사이의 경계를 명확히 유지합니다.
fun SleepSessionEntity.toDomain(): SleepSession = SleepSession(
    id = id,
    startTime = startTime,
    endTime = endTime,
    totalMinutes = totalMinutes,
    sleepScore = sleepScore,
    consistencyScore = consistencyScore,
    latencyMinutes = latencyMinutes,
    awakeMinutes = awakeMinutes,
    source = source,
)

fun SleepSession.toEntity(): SleepSessionEntity = SleepSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    totalMinutes = totalMinutes,
    sleepScore = sleepScore,
    consistencyScore = consistencyScore,
    latencyMinutes = latencyMinutes,
    awakeMinutes = awakeMinutes,
    source = source,
)

fun DrowsinessEventEntity.toDomain(): DrowsinessEvent = DrowsinessEvent(
    id = id,
    timestamp = timestamp,
    severity = severity,
    durationMinutes = durationMinutes,
    label = label,
    deviceId = deviceId,
    sessionId = sessionId,
)

fun DrowsinessEvent.toEntity(): DrowsinessEventEntity = DrowsinessEventEntity(
    id = id,
    timestamp = timestamp,
    severity = severity,
    durationMinutes = durationMinutes,
    label = label,
    deviceId = deviceId,
    sessionId = sessionId,
)

fun StudySessionState.toEntity(): StudySessionEntity {
    val summary = latestSummary
    return StudySessionEntity(
        id = sessionId ?: error("sessionId is required to persist study session state"),
        startedAt = startedAt ?: LocalDateTime.now(),
        endedAt = summary?.receivedAt,
        phase = phase.name,
        latestRiskState = latestRisk?.state,
        latestFusedScore = latestRisk?.fusedScore,
        alertCount = summary?.totalAlerts ?: if (latestAlert != null) 1 else 0,
        summaryMode = summary?.mode,
        summaryReason = summary?.summaryReason,
        peakFusedScore = summary?.peakFusedScore,
    )
}

fun StudySessionEntity.toSummary(): PiSessionSummary? {
    if (endedAt == null) return null
    return PiSessionSummary(
        sessionId = id,
        sequence = -1L,
        finalState = phase,
        totalAlerts = alertCount,
        peakFusedScore = peakFusedScore,
        mode = summaryMode,
        summaryReason = summaryReason,
        receivedAt = endedAt,
    )
}

fun WatchHeartRateSampleEntity.toDomain(): WatchHeartRateSample = WatchHeartRateSample(
    sessionId = sessionId,
    messageSequence = messageSequence,
    sampleSeq = sampleSeq,
    sensorTimestampMs = sensorTimestampMs,
    bpm = bpm,
    hrStatus = hrStatus,
    ibiMs = ibiMsSerialized.split(",").filter { it.isNotBlank() }.map(String::toInt),
    ibiStatus = ibiStatusSerialized.split(",").filter { it.isNotBlank() }.map(String::toInt),
    deliveryMode = deliveryMode,
    receivedAt = receivedAt,
)

fun WatchHeartRateSample.toEntity(relayState: String = "pending"): WatchHeartRateSampleEntity = WatchHeartRateSampleEntity(
    id = "$sessionId:$sampleSeq",
    sessionId = sessionId,
    messageSequence = messageSequence,
    sampleSeq = sampleSeq,
    sensorTimestampMs = sensorTimestampMs,
    bpm = bpm,
    hrStatus = hrStatus,
    ibiMsSerialized = ibiMs.joinToString(","),
    ibiStatusSerialized = ibiStatus.joinToString(","),
    deliveryMode = deliveryMode,
    receivedAt = receivedAt,
    relayState = relayState,
)

fun WatchCursorEntity.toDomain(): WatchCursor = WatchCursor(
    sessionId = sessionId,
    highestContiguousSampleSeq = highestContiguousSampleSeq,
    pendingBackfillFrom = pendingBackfillFrom,
    lastAckSentAt = lastAckSentAt,
)

fun WatchCursor.toEntity(): WatchCursorEntity = WatchCursorEntity(
    sessionId = sessionId,
    highestContiguousSampleSeq = highestContiguousSampleSeq,
    pendingBackfillFrom = pendingBackfillFrom,
    lastAckSentAt = lastAckSentAt,
)

fun StudyPlanEntity.toDomain(): StudyPlan = StudyPlan(
    id = id,
    startTime = startTime,
    endTime = endTime,
    focusHours = focusHours,
    days = daysSerialized.split(",").filter { it.isNotBlank() }.map(DayOfWeek::valueOf).toSet(),
    breakPreferenceMinutes = breakPreferenceMinutes,
    autoBreakEnabled = autoBreakEnabled,
)

fun StudyPlan.toEntity(): StudyPlanEntity = StudyPlanEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    focusHours = focusHours,
    daysSerialized = days.joinToString(",") { it.name },
    breakPreferenceMinutes = breakPreferenceMinutes,
    autoBreakEnabled = autoBreakEnabled,
)

fun ExamScheduleEntity.toDomain(): ExamSchedule = ExamSchedule(
    id = id,
    name = name,
    date = date,
    startTime = startTime,
    endTime = endTime,
    location = location,
    priority = priority,
    syncEnabled = syncEnabled,
)

fun ExamSchedule.toEntity(): ExamScheduleEntity = ExamScheduleEntity(
    id = id,
    name = name,
    date = date,
    startTime = startTime,
    endTime = endTime,
    location = location,
    priority = priority,
    syncEnabled = syncEnabled,
)

fun RecommendationSnapshotEntity.toDomain(): RecommendationSnapshot = RecommendationSnapshot(
    id = id,
    recommendedBedtime = recommendedBedtime,
    recommendedWakeTime = recommendedWakeTime,
    targetSleepMinutes = targetSleepMinutes,
    reason = reason,
    routineShiftMinutes = routineShiftMinutes,
    tips = tipsSerialized.split("||").filter { it.isNotBlank() }.map { encoded ->
        val parts = encoded.split("::")
        RecommendationTip(
            title = parts.getOrElse(0) { "" },
            body = parts.getOrElse(1) { "" },
            iconKey = parts.getOrElse(2) { "insights" },
        )
    },
    generatedAt = generatedAt,
)

fun RecommendationSnapshot.toEntity(): RecommendationSnapshotEntity = RecommendationSnapshotEntity(
    id = id,
    recommendedBedtime = recommendedBedtime,
    recommendedWakeTime = recommendedWakeTime,
    targetSleepMinutes = targetSleepMinutes,
    reason = reason,
    routineShiftMinutes = routineShiftMinutes,
    tipsSerialized = tips.joinToString("||") { "${it.title}::${it.body}::${it.iconKey}" },
    generatedAt = generatedAt,
)
