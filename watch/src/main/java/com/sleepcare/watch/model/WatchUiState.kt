package com.sleepcare.watch.model

import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateSample

// 워치 앱에서 보여줄 네 가지 최상위 화면입니다.
enum class WatchScreen {
    ConnectionWaiting,
    ActiveSession,
    Alerting,
    WatchSettings,
}

// WatchSessionStore가 관리하는 단일 UI 상태입니다.
// 실제 센서 세션 상태와 데모/플레이스홀더 UI 값이 함께 들어 있습니다.
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
    val lastIncomingPath: String? = null,
    val messageLog: List<String> = emptyList(),
)
