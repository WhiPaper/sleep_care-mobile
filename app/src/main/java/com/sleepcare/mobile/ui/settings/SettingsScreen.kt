package com.sleepcare.mobile.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sleepcare.mobile.data.source.HealthConnectSleepDataSource
import com.sleepcare.mobile.data.source.HealthConnectSleepState
import com.sleepcare.mobile.domain.NotificationPreferences
import com.sleepcare.mobile.domain.SettingsRepository
import com.sleepcare.mobile.domain.SleepRepository
import com.sleepcare.mobile.ui.components.GlassCard
import com.sleepcare.mobile.ui.components.toDisplayDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val HEALTH_CONNECT_PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

// 설정 화면에 필요한 알림 설정, 동기화 문구, Health Connect 권한 상태입니다.
data class SettingsUiState(
    val preferences: NotificationPreferences = NotificationPreferences(),
    val lastSyncText: String = "Health Connect 수면 동기화 대기 중",
    val canRequestHealthConnectPermission: Boolean = false,
    val healthConnectState: HealthConnectSleepState = HealthConnectSleepState.Checking,
)

// 알림 토글, 기기 관리, Health Connect 권한, 데이터 초기화를 제공하는 설정 화면입니다.
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    onOpenDevices: () -> Unit,
    onResetCompleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var healthConnectHelpText by rememberSaveable { mutableStateOf<String?>(null) }
    val healthConnectPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )
    // 설정 화면으로 돌아올 때 Health Connect 권한/데이터 상태를 다시 확인합니다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSleepSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(healthConnectPermissions)) {
            healthConnectHelpText = null
            viewModel.refreshSleepSync()
        } else {
            healthConnectHelpText =
                "권한 창이 뜨지 않거나 바로 닫히면 Health Connect 설정에서 이 앱 권한을 직접 허용해 주세요."
        }
    }
    LazyColumn(
        modifier = Modifier.padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("설정", style = MaterialTheme.typography.headlineMedium) }
        item {
            SettingSwitchCard(
                title = "졸음 알림",
                description = "중요 졸음 이벤트가 감지되면 앱 알림으로 알려줍니다.",
                checked = uiState.preferences.drowsinessAlertsEnabled,
                onCheckedChange = {
                    viewModel.updatePreferences(uiState.preferences.copy(drowsinessAlertsEnabled = it))
                },
            )
        }
        item {
            SettingSwitchCard(
                title = "수면 리마인더",
                description = "추천 취침 시각이 가까워지면 미리 준비 시간을 알려줍니다.",
                checked = uiState.preferences.sleepRemindersEnabled,
                onCheckedChange = {
                    viewModel.updatePreferences(uiState.preferences.copy(sleepRemindersEnabled = it))
                },
            )
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("기기 관리", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Galaxy Watch, Health Connect, 그리고 Raspberry Pi 연결 상태를 함께 확인합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenDevices, modifier = Modifier.fillMaxWidth()) {
                        Text("기기 연결 화면 열기")
                    }
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("연동 상태", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Galaxy Watch 세션 연동, Health Connect 수면 동기화, Raspberry Pi 졸음 기록을 함께 사용합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        uiState.lastSyncText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (healthConnectHelpText != null) {
                        Text(
                            text = healthConnectHelpText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.canRequestHealthConnectPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(healthConnectPermissions) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Health Connect 권한 요청")
                        }
                        Button(
                            onClick = {
                                openHealthConnectSettings(context)
                                healthConnectHelpText =
                                    "Health Connect 화면에서 Sleep Care 앱 권한을 열어 수면 읽기를 허용한 뒤 다시 돌아오면 상태를 재확인합니다."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Health Connect에서 직접 권한 열기")
                        }
                    } else if (uiState.healthConnectState is HealthConnectSleepState.ProviderUpdateRequired) {
                        Button(
                            onClick = { openHealthConnectUpdate(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Health Connect 업데이트")
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("데이터 초기화", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "온보딩 상태와 샘플 데이터를 다시 초기 상태로 되돌립니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onResetCompleted, modifier = Modifier.fillMaxWidth()) {
                        Text("앱 데이터 초기화")
                    }
                }
            }
        }
    }
}

// 라벨과 설명이 붙은 공통 설정 스위치 카드입니다.
@Composable
private fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@HiltViewModel
// DataStore 설정과 Health Connect 수면 동기화 상태를 설정 화면용 문구로 조합합니다.
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sleepRepository: SleepRepository,
    private val sleepDataSource: HealthConnectSleepDataSource,
) : ViewModel() {
    val uiState = combine(
        settingsRepository.observeNotificationPreferences(),
        settingsRepository.observeLastSyncState(),
        sleepDataSource.state,
    ) { preferences, lastSync, sleepState ->
        SettingsUiState(
            preferences = preferences,
            lastSyncText = buildString {
                append(
                    if (lastSync.drowsinessSyncedAt != null) {
                        "Pi 졸음 ${lastSync.drowsinessSyncedAt.toDisplayDateTime()}"
                    } else {
                        "Pi 졸음 기록 없음"
                    }
                )
                append(sleepState.toSettingsCopy(lastSync.sleepSyncedAt))
            },
            canRequestHealthConnectPermission = sleepState is HealthConnectSleepState.PermissionDenied,
            healthConnectState = sleepState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updatePreferences(preferences: NotificationPreferences) {
        viewModelScope.launch {
            settingsRepository.updateNotificationPreferences(preferences)
        }
    }

    fun refreshSleepSync() {
        viewModelScope.launch {
            sleepRepository.refreshFromSource()
        }
    }
}

// Health Connect 내부 상태를 설정 화면의 한 줄 동기화 상태 문구로 바꿉니다.
private fun HealthConnectSleepState.toSettingsCopy(lastSyncedAt: java.time.LocalDateTime?): String = when (this) {
    HealthConnectSleepState.Ready -> if (lastSyncedAt != null) {
        " · Health Connect 수면 ${lastSyncedAt.toDisplayDateTime()}"
    } else {
        " · Health Connect 수면 동기화 완료"
    }
    HealthConnectSleepState.Checking -> " · Health Connect 상태 확인 중"
    HealthConnectSleepState.NoData -> " · Health Connect 수면 데이터 없음"
    HealthConnectSleepState.PermissionDenied -> " · Health Connect 수면 권한 없음"
    HealthConnectSleepState.Unavailable -> " · Health Connect 사용 불가"
    HealthConnectSleepState.ProviderUpdateRequired -> " · Health Connect 업데이트 필요"
    is HealthConnectSleepState.Error -> " · Health Connect 오류"
}

// Health Connect 앱 내부의 데이터/권한 관리 화면을 열고, 실패하면 일반 설정 화면으로 보냅니다.
private fun openHealthConnectSettings(context: Context) {
    val intent = runCatching {
        HealthConnectClient.getHealthConnectManageDataIntent(context, HEALTH_CONNECT_PROVIDER_PACKAGE_NAME)
    }.getOrElse {
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openHealthConnectUpdate(context)
    }
}

// Health Connect가 없거나 업데이트가 필요할 때 Play Store 또는 웹 페이지를 엽니다.
private fun openHealthConnectUpdate(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding")
    ).apply {
        setPackage("com.android.vending")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE_NAME")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(webIntent)
    }
}
