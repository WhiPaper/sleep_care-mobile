package com.sleepcare.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Hilt가 앱 시작 시 의존성 그래프를 만들 수 있도록 표시하는 Application 클래스입니다.
@HiltAndroidApp
class SleepCareApplication : Application()
