package com.sleepcare.mobile.health

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import com.sleepcare.mobile.MainActivity
import com.sleepcare.mobile.ui.theme.SleepCareTheme

class HealthConnectOnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SleepCareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HealthConnectOnboardingScreen(
                        onOpenApp = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onFinish = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthConnectOnboardingScreen(
    onOpenApp: () -> Unit,
    onFinish: () -> Unit,
) {
    val permissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )
    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        onOpenApp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Health Connect 연결", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sleep Care는 최근 수면 세션을 읽어 수면 분석과 루틴 추천에 반영합니다.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "요청 권한: 수면 데이터 읽기",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { launcher.launch(permissions) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Health Connect 권한 허용")
        }
        OutlinedButton(
            onClick = onOpenApp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("앱에서 계속")
        }
        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("나중에")
        }
    }
}
