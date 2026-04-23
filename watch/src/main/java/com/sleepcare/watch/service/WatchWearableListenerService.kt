package com.sleepcare.watch.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sleepcare.watch.contracts.WatchPaths

class WatchWearableListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WatchPaths.SessionStart -> WatchSensorTrackingService.startFromPhone(applicationContext, messageEvent.data)
            WatchPaths.SessionStop -> WatchSensorTrackingService.stopFromPhone(applicationContext, messageEvent.data)
            WatchPaths.FlushPolicyUpdate -> WatchSensorTrackingService.updateFlushPolicyFromPhone(applicationContext, messageEvent.data)
            WatchPaths.BackfillRequest -> WatchSensorTrackingService.requestBackfillFromPhone(applicationContext, messageEvent.data)
            WatchPaths.AlertVibrate -> WatchSensorTrackingService.triggerAlertFromPhone(applicationContext, messageEvent.data)
        }
    }
}
