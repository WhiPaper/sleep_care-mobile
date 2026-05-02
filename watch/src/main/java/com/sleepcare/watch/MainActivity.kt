package com.sleepcare.watch

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.sleepcare.watch.runtime.WatchSessionStore

// Wear OS 앱의 진입점입니다.
// 센서 권한을 요청한 뒤 WatchApp Compose 트리를 띄웁니다.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                // 권한 결과는 전역 Store에 반영해 설정 화면과 센서 시작 흐름이 함께 참고합니다.
                WatchSessionStore.updatePermissions(grants.values.all { it })
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BODY_SENSORS,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                    ),
                )
            }

            WatchApp()
        }
    }
}
