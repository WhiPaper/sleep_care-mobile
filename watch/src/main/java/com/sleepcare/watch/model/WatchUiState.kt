package com.sleepcare.watch.model

import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateSample

enum class WatchScreen {
    ConnectionWaiting,
    ActiveSession,
    Alerting,
    WatchSettings,
}

data class WatchUiState(
    val screen: WatchScreen = WatchScreen.ConnectionWaiting,
    val sessionId: String? = null,
    val connectionTitle: String = "Ready to Sync",
    val connectionSubtitle: String = "Open app on phone to start session",
    val connectionBadge: String = "Waiting...",
    val trackingStatus: String = "Foreground tracking idle",
    val permissionsGranted: Boolean = true,
    val permissionsStatusLabel: String = "Granted",
    val sleepSyncStatus: String = "Pending",
    val latestHeartRate: Int = 72,
    val latestIbiMs: Int = 840,
    val lastSyncLabel: String = "Last sync: 12s ago",
    val alertTitle: String = "Drowsiness Risk",
    val alertBody: String = "Critical fatigue levels detected. Please take a break.",
    val alertBadge: String = "94% vigilance drop",
    val alertActionLabel: String = "Dismiss",
    val flushPolicy: WatchFlushPolicy = WatchFlushPolicy(),
    val latestSample: WatchHeartRateSample? = null,
)
