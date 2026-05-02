package com.sleepcare.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.sleepcare.watch.model.WatchScreen
import com.sleepcare.watch.runtime.WatchSessionStore
import com.sleepcare.watch.ui.screen.AlertingScreen
import com.sleepcare.watch.ui.screen.ConnectionWaitingScreen
import com.sleepcare.watch.ui.screen.ActiveSessionScreen
import com.sleepcare.watch.ui.screen.WatchSettingsScreen
import com.sleepcare.watch.ui.theme.SleepCareWatchTheme

// 워치 앱의 최상위 Compose 화면입니다.
// WatchSessionStore의 screen 값에 따라 대기/세션/알림/설정 화면을 전환합니다.
@Composable
fun WatchApp() {
    val state by WatchSessionStore.state.collectAsState()

    SleepCareWatchTheme {
        Scaffold(
            timeText = { TimeText() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00353D).copy(alpha = 0.35f),
                                Color(0xFF111415),
                                Color(0xFF111415),
                            ),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                // 워치 화면은 네비게이션 라이브러리 대신 단순 상태 전환으로 충분합니다.
                when (state.screen) {
                    WatchScreen.ConnectionWaiting -> ConnectionWaitingScreen(
                        state = state,
                        onOpenSettings = { WatchSessionStore.showSettings() },
                        onStartDemoSession = { WatchSessionStore.startDemoSession() },
                        onRetryConnection = {
                            WatchSessionStore.showConnectionWaiting("Open app on phone to start session")
                        },
                    )

                    WatchScreen.ActiveSession -> ActiveSessionScreen(
                        state = state,
                        onOpenSettings = { WatchSessionStore.showSettings() },
                        onTriggerAlert = { WatchSessionStore.markAlerting() },
                        onStopSession = { WatchSessionStore.stopTracking("Stopped from watch UI") },
                    )

                    WatchScreen.Alerting -> AlertingScreen(
                        state = state,
                        onDismiss = { WatchSessionStore.dismissAlert() },
                    )

                    WatchScreen.WatchSettings -> WatchSettingsScreen(
                        state = state,
                        onBackToConnection = { WatchSessionStore.showConnectionWaiting() },
                        onBackToSession = { WatchSessionStore.restorePrimaryScreen() },
                        onTogglePermissions = { WatchSessionStore.updatePermissions(!state.permissionsGranted) },
                        onRefreshSleepSync = { WatchSessionStore.updateSleepSyncStatus("Managed on phone") },
                    )
                }
            }
        }
    }
}
