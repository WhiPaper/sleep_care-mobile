package com.sleepcare.watch.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

// 휴대폰 앱에서 보낸 Wear OS Data Layer 메시지를 받아 공통 라우터로 넘깁니다.
// 화면이 꺼져 있어도 동작해야 하는 운영 경로이므로 manifest listener는 계속 유지합니다.
class WatchWearableListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        WatchMessageRouter.route(applicationContext, messageEvent, source = "manifest-service")
    }
}
