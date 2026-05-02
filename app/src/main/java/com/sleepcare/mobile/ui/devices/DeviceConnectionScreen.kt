package com.sleepcare.mobile.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sleepcare.mobile.data.source.PiPairingCodec
import com.sleepcare.mobile.domain.ConnectionStatus
import com.sleepcare.mobile.domain.ConnectedDeviceState
import com.sleepcare.mobile.domain.DeviceConnectionRepository
import com.sleepcare.mobile.domain.DeviceType
import com.sleepcare.mobile.domain.TrustedPiDevice
import com.sleepcare.mobile.ui.components.DeviceStatusCard
import com.sleepcare.mobile.ui.components.DeviceVisualStatus
import com.sleepcare.mobile.ui.components.GlassCard
import com.sleepcare.mobile.ui.components.toDisplayDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 기기 연결 화면은 Repository가 합쳐 준 Pi/Watch 상태 리스트를 그대로 표시합니다.
data class DevicesUiState(
    val devices: List<ConnectedDeviceState> = emptyList(),
    val trustedPi: TrustedPiDevice? = null,
)

// Galaxy Watch와 Raspberry Pi 연결 상태를 카드로 보여주고 재시도/연결 해제를 제공합니다.
@Composable
fun DeviceConnectionScreen(
    paddingValues: PaddingValues,
    onOpenPiPairing: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("기기 연결", style = MaterialTheme.typography.headlineMedium) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Raspberry Pi 등록", style = MaterialTheme.typography.titleMedium)
                    val trustedPi = uiState.trustedPi
                    if (trustedPi == null) {
                        Text(
                            "Pi 화면의 QR 코드를 스캔해 이 앱이 신뢰할 SPKI fingerprint를 등록합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${trustedPi.displayName} · ${trustedPi.deviceId}\nSPKI ${PiPairingCodec.shortPin(trustedPi.spkiSha256)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onOpenPiPairing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (trustedPi == null) "새 Pi QR 등록" else "QR로 재등록")
                    }
                    if (trustedPi != null) {
                        OutlinedButton(onClick = viewModel::forgetPi, modifier = Modifier.fillMaxWidth()) {
                            Text("등록 해제")
                        }
                    }
                }
            }
        }
        items(uiState.devices) { device ->
            DeviceStatusCard(
                deviceName = if (device.deviceType == DeviceType.Smartwatch) "Galaxy Watch" else device.deviceName,
                status = device.status.toVisualStatus(),
                subtitle = if (device.deviceType == DeviceType.Smartwatch) {
                    "심박/IBI 수집과 워치 진동 보조 경고용"
                } else {
                    "공부 중 졸음 감지 이벤트 수신용"
                },
                connectionDetails = if (device.deviceType == DeviceType.Smartwatch) {
                    buildString {
                        append(device.details ?: "Galaxy Watch를 찾는 중")
                        append("\n수면 데이터 연동은 워치 앱 구현 범위에서 함께 정리합니다.")
                        device.lastSeenAt?.let { append("\n마지막 확인 ${it.toDisplayDateTime()}") }
                    }
                } else {
                    buildString {
                        append(device.details ?: "준비 중")
                        device.lastSeenAt?.let { append("\n마지막 확인 ${it.toDisplayDateTime()}") }
                    }
                },
                statusLabel = if (device.deviceType == DeviceType.Smartwatch && device.status == ConnectionStatus.Connected) {
                    "Galaxy Watch 연결됨"
                } else {
                    null
                },
                actionLabel = when (device.status) {
                    ConnectionStatus.Connected -> "연결 해제"
                    ConnectionStatus.Scanning -> null
                    ConnectionStatus.Disconnected, ConnectionStatus.Failed -> "다시 시도"
                },
                onActionClick = {
                    when (device.status) {
                        ConnectionStatus.Connected -> viewModel.disconnect(device.deviceType)
                        ConnectionStatus.Scanning -> Unit
                        ConnectionStatus.Disconnected, ConnectionStatus.Failed -> viewModel.retry(device.deviceType)
                    }
                },
            )
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("연결 안내", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "모바일 앱은 Galaxy Watch와 Wear OS Data Layer로 연결되고, Raspberry Pi는 로컬 Wi-Fi에서 찾습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = viewModel::startScan, modifier = Modifier.fillMaxWidth()) {
                        Text("로컬 Pi 다시 찾기")
                    }
                }
            }
        }
    }
}

@HiltViewModel
// 기기 상태 Flow를 UI 상태로 감싸고 버튼 명령을 Repository로 전달합니다.
class DevicesViewModel @Inject constructor(
    private val deviceConnectionRepository: DeviceConnectionRepository,
) : ViewModel() {
    val uiState = combine(
        deviceConnectionRepository.observeDevices(),
        deviceConnectionRepository.observeTrustedPi(),
    ) { devices, trustedPi ->
        DevicesUiState(devices = devices, trustedPi = trustedPi)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    fun startScan() {
        viewModelScope.launch { deviceConnectionRepository.startScan() }
    }

    fun retry(deviceType: DeviceType) {
        viewModelScope.launch { deviceConnectionRepository.retryConnection(deviceType) }
    }

    fun disconnect(deviceType: DeviceType) {
        viewModelScope.launch { deviceConnectionRepository.disconnect(deviceType) }
    }

    fun forgetPi() {
        viewModelScope.launch { deviceConnectionRepository.forgetPi() }
    }
}

// 도메인 연결 상태를 카드 컴포넌트가 이해하는 시각 상태로 변환합니다.
private fun ConnectionStatus.toVisualStatus(): DeviceVisualStatus = when (this) {
    ConnectionStatus.Connected -> DeviceVisualStatus.Connected
    ConnectionStatus.Scanning -> DeviceVisualStatus.Connecting
    ConnectionStatus.Disconnected -> DeviceVisualStatus.Disconnected
    ConnectionStatus.Failed -> DeviceVisualStatus.Error
}
