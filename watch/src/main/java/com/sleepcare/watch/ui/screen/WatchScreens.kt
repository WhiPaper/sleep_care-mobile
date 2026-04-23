package com.sleepcare.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sleepcare.watch.model.WatchScreen
import com.sleepcare.watch.model.WatchUiState

@Composable
fun ConnectionWaitingScreen(
    state: WatchUiState,
    onOpenSettings: () -> Unit,
    onStartDemoSession: () -> Unit,
    onRetryConnection: () -> Unit,
) {
    ScreenContainer {
        BrandLine()
        Spacer(Modifier.height(8.dp))
        HaloOrb(
            icon = Icons.Filled.WifiOff,
            iconTint = MaterialTheme.colors.primary,
            title = state.connectionTitle,
            subtitle = state.connectionSubtitle,
            badge = state.connectionBadge,
        )
        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = onStartDemoSession,
            label = { Text("Open Session") },
            secondaryLabel = { Text("Start watch runtime") },
            colors = ChipDefaults.primaryChipColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionChip(text = "Retry", icon = Icons.Filled.Refresh, onClick = onRetryConnection)
            SmallActionChip(text = "Settings", icon = Icons.Filled.Settings, onClick = onOpenSettings)
        }
    }
}

@Composable
fun ActiveSessionScreen(
    state: WatchUiState,
    onOpenSettings: () -> Unit,
    onTriggerAlert: () -> Unit,
    onStopSession: () -> Unit,
) {
    ScreenContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = "SENSOR", icon = Icons.Filled.Bluetooth, active = true)
            Spacer(Modifier.weight(1f))
            SmallIconAction(Icons.Filled.Settings, onOpenSettings)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color(0xFF111415))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.latestHeartRate}",
                    style = MaterialTheme.typography.display1,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    textAlign = TextAlign.Center,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFB7C0FF),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "BPM",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.caption3,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Chip(
                    onClick = onTriggerAlert,
                    label = { Text("IBI: ${state.latestIbiMs}ms") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.lastSyncLabel,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.caption3,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionChip(text = "Alert", icon = Icons.Filled.NotificationsActive, onClick = onTriggerAlert)
            SmallActionChip(text = "Stop", icon = Icons.Filled.DirectionsRun, onClick = onStopSession)
        }
    }
}

@Composable
fun AlertingScreen(
    state: WatchUiState,
    onDismiss: () -> Unit,
) {
    ScreenContainer {
        BrandLine()
        Spacer(Modifier.height(10.dp))
        HaloOrb(
            icon = Icons.Filled.NotificationsActive,
            iconTint = Color(0xFFBDC2FF),
            title = state.alertTitle,
            subtitle = state.alertBody,
            badge = state.alertBadge,
            badgeColor = Color(0xFF44D8F1),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
        ) {
            Text(state.alertActionLabel)
        }
    }
}

@Composable
fun WatchSettingsScreen(
    state: WatchUiState,
    onBackToConnection: () -> Unit,
    onBackToSession: () -> Unit,
    onTogglePermissions: () -> Unit,
    onRefreshSleepSync: () -> Unit,
) {
    ScreenContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = "Permissions",
                subtitle = state.permissionsStatusLabel,
                icon = Icons.Filled.CheckCircle,
                onClick = onTogglePermissions,
            )
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                title = "Sleep Log",
                subtitle = state.sleepSyncStatus,
                icon = Icons.Filled.Refresh,
                onClick = onRefreshSleepSync,
            )
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                title = "Sensor Info",
                subtitle = state.trackingStatus,
                icon = Icons.Filled.SignalCellularAlt,
                onClick = onBackToSession,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallIconAction(Icons.Filled.WifiOff, onBackToConnection)
            SmallIconAction(Icons.Filled.DirectionsRun, onBackToSession)
            SmallIconAction(Icons.Filled.Settings, onBackToConnection)
        }
    }
}

@Composable
private fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun BrandLine() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = "NOCTURNE SCIENCE",
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.caption3,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HaloOrb(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color = MaterialTheme.colors.secondary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1C1D)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282A2C)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.82f),
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Chip(
            onClick = { },
            label = { Text(badge, color = badgeColor) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(title) },
        secondaryLabel = { Text(subtitle) },
        icon = {
            Icon(imageVector = icon, contentDescription = null)
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun StatusPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
) {
    Chip(
        onClick = { },
        label = { Text(text) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        colors = if (active) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
    )
}

@Composable
private fun SmallActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(text) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun SmallIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(" ") },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
