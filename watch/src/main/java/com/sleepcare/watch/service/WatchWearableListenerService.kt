package com.sleepcare.watch.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sleepcare.watch.contracts.WatchPaths

// 휴대폰 앱에서 보낸 Wear OS Data Layer 메시지를 받아 전면 추적 서비스 Intent로 변환합니다.
class WatchWearableListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // 메시지 path는 명령 종류이고, data는 WatchProtocolCodec JSON payload입니다.
        when (messageEvent.path) {
            WatchPaths.SessionStart -> WatchSensorTrackingService.startFromPhone(applicationContext, messageEvent.data)
            WatchPaths.SessionStop -> WatchSensorTrackingService.stopFromPhone(applicationContext, messageEvent.data)
            WatchPaths.FlushPolicyUpdate -> WatchSensorTrackingService.updateFlushPolicyFromPhone(applicationContext, messageEvent.data)
            WatchPaths.BackfillRequest -> WatchSensorTrackingService.requestBackfillFromPhone(applicationContext, messageEvent.data)
            WatchPaths.AlertVibrate -> WatchSensorTrackingService.triggerAlertFromPhone(applicationContext, messageEvent.data)
        }
    }
}
