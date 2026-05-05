package com.sleepcare.watch

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.sleepcare.watch.runtime.WatchSessionStore
import com.sleepcare.watch.service.WatchMessageRouter

// Wear OS 앱의 진입점입니다.
// 센서 권한을 요청한 뒤 WatchApp Compose 트리를 띄웁니다.
class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {
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

    override fun onStart() {
        super.onStart()
        // 워치 화면을 열어 둔 개발/테스트 상황에서는 live listener도 붙여 manifest listener 문제를 즉시 분리합니다.
        Wearable.getMessageClient(this)
            .addListener(this)
            .addOnSuccessListener { Log.d(TAG, "activity message listener registered") }
            .addOnFailureListener { throwable -> Log.d(TAG, "activity message listener failed", throwable) }
    }

    override fun onStop() {
        Wearable.getMessageClient(this).removeListener(this)
        super.onStop()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        WatchMessageRouter.route(applicationContext, messageEvent, source = "activity-live")
    }

    private companion object {
        private const val TAG = "SleepCareWatch"
    }
}
