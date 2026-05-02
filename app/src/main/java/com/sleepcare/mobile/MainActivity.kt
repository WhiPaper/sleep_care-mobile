package com.sleepcare.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sleepcare.mobile.navigation.SleepCareApp
import com.sleepcare.mobile.ui.theme.SleepCareTheme
import dagger.hilt.android.AndroidEntryPoint

// 모바일 앱의 Android 진입점입니다.
// 여기서는 엣지 투 엣지 설정과 최상위 Compose 트리 연결만 담당합니다.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepCareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SleepCareApp()
                }
            }
        }
    }
}
