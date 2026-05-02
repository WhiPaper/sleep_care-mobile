package com.sleepcare.mobile.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepcare.mobile.ui.theme.SleepCareTheme

// Health Connect 권한 요청 전에 왜 수면 읽기 권한이 필요한지 설명하는 Activity입니다.
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SleepCareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Health Connect 권한 안내", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Sleep Care는 Health Connect에서 최근 수면 세션을 읽어 수면 길이, 각성 시간, 규칙성을 분석합니다.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "읽은 데이터는 앱 내 수면 분석과 루틴 추천에만 사용되며, 이 화면에서는 수면 읽기 권한만 요청합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "실제 서비스 배포 시에는 이 내용이 Play Console에 등록한 개인정보처리방침과 일치해야 합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("닫기")
                        }
                    }
                }
            }
        }
    }
}
