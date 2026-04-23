package com.sleepcare.mobile.data.source

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sleepcare.mobile.domain.ScoreCalculator
import com.sleepcare.mobile.domain.SleepSession
import com.sleepcare.mobile.domain.WatchSleepDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface HealthConnectSleepState {
    data object Checking : HealthConnectSleepState
    data object Ready : HealthConnectSleepState
    data object NoData : HealthConnectSleepState
    data object PermissionDenied : HealthConnectSleepState
    data object Unavailable : HealthConnectSleepState
    data object ProviderUpdateRequired : HealthConnectSleepState
    data class Error(val message: String) : HealthConnectSleepState
}

@Singleton
class HealthConnectSleepDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatchSleepDataSource {
    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    private val _state = MutableStateFlow<HealthConnectSleepState>(HealthConnectSleepState.Checking)
    val state: StateFlow<HealthConnectSleepState> = _state.asStateFlow()

    override suspend fun readRecentSleepSessions(): List<SleepSession> = withContext(Dispatchers.IO) {
        _state.value = HealthConnectSleepState.Checking

        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                _state.value = HealthConnectSleepState.Unavailable
                return@withContext emptyList()
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                _state.value = HealthConnectSleepState.ProviderUpdateRequired
                return@withContext emptyList()
            }
        }

        val client = try {
            HealthConnectClient.getOrCreate(context)
        } catch (_: IllegalStateException) {
            _state.value = HealthConnectSleepState.Unavailable
            return@withContext emptyList()
        } catch (_: UnsupportedOperationException) {
            _state.value = HealthConnectSleepState.Unavailable
            return@withContext emptyList()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            _state.value = HealthConnectSleepState.Error(throwable.message ?: "Health Connect 클라이언트를 만들 수 없습니다.")
            return@withContext emptyList()
        }

        val grantedPermissions = try {
            client.permissionController.getGrantedPermissions()
        } catch (_: SecurityException) {
            _state.value = HealthConnectSleepState.PermissionDenied
            return@withContext emptyList()
        } catch (_: IllegalStateException) {
            _state.value = HealthConnectSleepState.Unavailable
            return@withContext emptyList()
        } catch (_: UnsupportedOperationException) {
            _state.value = HealthConnectSleepState.Unavailable
            return@withContext emptyList()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            _state.value = HealthConnectSleepState.Error(throwable.message ?: "Health Connect 권한을 확인할 수 없습니다.")
            return@withContext emptyList()
        }

        if (!grantedPermissions.containsAll(requiredPermissions)) {
            _state.value = HealthConnectSleepState.PermissionDenied
            return@withContext emptyList()
        }

        val now = Instant.now()
        val start = now.minus(Duration.ofDays(60))
        val records = try {
            client.readRecords(
                ReadRecordsRequest<SleepSessionRecord>(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now),
                )
            ).records
        } catch (_: SecurityException) {
            _state.value = HealthConnectSleepState.PermissionDenied
            return@withContext emptyList()
        } catch (_: IllegalStateException) {
            _state.value = HealthConnectSleepState.Unavailable
            return@withContext emptyList()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            _state.value = HealthConnectSleepState.Error(throwable.message ?: "Health Connect 수면 세션을 읽지 못했습니다.")
            return@withContext emptyList()
        }

        val sessions = records
            .sortedByDescending { it.startTime }
            .map { it.toDomain() }

        _state.value = if (sessions.isEmpty()) {
            HealthConnectSleepState.NoData
        } else {
            HealthConnectSleepState.Ready
        }
        sessions
    }

    private fun SleepSessionRecord.toDomain(): SleepSession {
        val zoneId = ZoneId.systemDefault()
        val totalMinutes = Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(0)
        val sortedStages = stages.sortedBy { it.startTime }
        val awakeMinutes = sortedStages.sumOf { stage ->
            if (stage.isAwakeStage()) stage.durationMinutes() else 0
        }
        val sleepMinutes = sortedStages.sumOf { stage ->
            if (stage.isSleepStage()) stage.durationMinutes() else 0
        }
        val latencyMinutes = sortedStages.firstOrNull { it.isSleepStage() }
            ?.let { Duration.between(startTime, it.startTime).toMinutes().toInt().coerceAtLeast(0) }
            ?: 0
        val consistencyScore = if (totalMinutes > 0) {
            ((sleepMinutes * 100) / totalMinutes).coerceIn(0, 100)
        } else {
            0
        }
        val sleepScore = ScoreCalculator.sleepQuality(
            totalMinutes = totalMinutes,
            consistencyScore = consistencyScore,
            latencyMinutes = latencyMinutes,
            awakeMinutes = awakeMinutes,
        )

        return SleepSession(
            id = metadata.id,
            startTime = startTime.atZone(zoneId).toLocalDateTime(),
            endTime = endTime.atZone(zoneId).toLocalDateTime(),
            totalMinutes = totalMinutes,
            sleepScore = sleepScore,
            consistencyScore = consistencyScore,
            latencyMinutes = latencyMinutes,
            awakeMinutes = awakeMinutes,
            source = "Health Connect",
        )
    }

    private fun SleepSessionRecord.Stage.durationMinutes(): Int =
        Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(0)

    private fun SleepSessionRecord.Stage.isAwakeStage(): Boolean = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> true
        else -> false
    }

    private fun SleepSessionRecord.Stage.isSleepStage(): Boolean = when (stage) {
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_DEEP,
        SleepSessionRecord.STAGE_TYPE_REM,
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> true
        else -> false
    }
}
