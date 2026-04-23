package com.sleepcare.watch.runtime

import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateSample
import com.sleepcare.watch.model.WatchScreen
import com.sleepcare.watch.model.WatchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WatchSessionStore {
    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    fun showConnectionWaiting(
        subtitle: String = "Open app on phone to start session",
        sessionId: String? = null,
    ) {
        _state.update {
            it.copy(
                screen = WatchScreen.ConnectionWaiting,
                sessionId = sessionId,
                connectionTitle = "Ready to Sync",
                connectionSubtitle = subtitle,
                connectionBadge = "Waiting...",
                trackingStatus = "Foreground tracking idle",
                lastSyncLabel = "Waiting for phone",
            )
        }
    }

    fun startDemoSession(sessionId: String = "demo-session") {
        _state.update {
            it.copy(
                screen = WatchScreen.ActiveSession,
                sessionId = sessionId,
                connectionTitle = "Ready to Sync",
                connectionSubtitle = "Foreground tracking is active",
                connectionBadge = "Sensor",
                trackingStatus = "Foreground tracking active",
                lastSyncLabel = "Last sync: just now",
                latestHeartRate = it.latestHeartRate.takeIf { value -> value > 0 } ?: 72,
                latestIbiMs = it.latestIbiMs.takeIf { value -> value > 0 } ?: 840,
            )
        }
    }

    fun restorePrimaryScreen() {
        _state.update {
            it.copy(
                screen = if (it.sessionId == null) WatchScreen.ConnectionWaiting else WatchScreen.ActiveSession,
            )
        }
    }

    fun markAlerting(
        badge: String = "94% vigilance drop",
        body: String = "Critical fatigue levels detected. Please take a break.",
    ) {
        _state.update {
            it.copy(
                screen = WatchScreen.Alerting,
                alertBadge = badge,
                alertBody = body,
            )
        }
    }

    fun dismissAlert() {
        restorePrimaryScreen()
    }

    fun stopTracking(reason: String = "Foreground tracking idle") {
        _state.update {
            it.copy(
                screen = WatchScreen.ConnectionWaiting,
                sessionId = null,
                connectionTitle = "Ready to Sync",
                connectionSubtitle = "Open app on phone to start session",
                connectionBadge = "Waiting...",
                trackingStatus = reason,
                lastSyncLabel = "Waiting for phone",
            )
        }
    }

    fun showSettings() {
        _state.update {
            it.copy(screen = WatchScreen.WatchSettings)
        }
    }

    fun updatePermissions(granted: Boolean) {
        _state.update {
            it.copy(
                permissionsGranted = granted,
                permissionsStatusLabel = if (granted) "Granted" else "Pending",
            )
        }
    }

    fun updateSleepSyncStatus(status: String) {
        _state.update {
            it.copy(sleepSyncStatus = status)
        }
    }

    fun updateFlushPolicy(policy: WatchFlushPolicy) {
        _state.update {
            it.copy(flushPolicy = policy)
        }
    }

    fun updateHeartRate(sample: WatchHeartRateSample) {
        _state.update {
            val nextScreen = when (it.screen) {
                WatchScreen.Alerting -> WatchScreen.Alerting
                WatchScreen.WatchSettings -> WatchScreen.WatchSettings
                else -> WatchScreen.ActiveSession
            }
            it.copy(
                screen = nextScreen,
                sessionId = sample.sessionId,
                latestSample = sample,
                latestHeartRate = sample.bpm,
                latestIbiMs = sample.ibiMs.firstOrNull() ?: it.latestIbiMs,
                lastSyncLabel = "Last sync: just now",
                trackingStatus = "Foreground tracking active",
            )
        }
    }
}
