package com.sleepcare.watch.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.sleepcare.watch.contracts.WatchCursor
import com.sleepcare.watch.contracts.WatchFlushPolicy
import com.sleepcare.watch.contracts.WatchHeartRateBatch
import com.sleepcare.watch.contracts.WatchHeartRateSample
import com.sleepcare.watch.contracts.WatchPaths
import com.sleepcare.watch.contracts.WatchProtocolCodec
import com.sleepcare.watch.contracts.WatchSessionConfig
import com.sleepcare.watch.runtime.WatchSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WatchSensorTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val buffer = WatchSampleBuffer()
    private val backend by lazy { WatchSensorBackendFactory.create(this) }
    private val messenger by lazy { WatchPhoneMessengerFactory.create(this) }

    private var currentConfig: WatchSessionConfig? = null
    private var trackingStarted = false

    override fun onCreate() {
        super.onCreate()
        WatchNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WatchSessionIntents.ACTION_START_SESSION -> handleStart(intent)
            WatchSessionIntents.ACTION_STOP_SESSION -> handleStop(intent)
            WatchSessionIntents.ACTION_UPDATE_FLUSH_POLICY -> handleFlushPolicy(intent)
            WatchSessionIntents.ACTION_BACKFILL_REQUEST -> handleBackfill(intent)
            WatchSessionIntents.ACTION_ALERT -> handleAlert(intent)
            WatchSessionIntents.ACTION_DISMISS_ALERT -> handleDismissAlert()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (trackingStarted || currentConfig != null) {
            serviceScope.launch {
                stopSessionInternal(reason = "service_destroyed", notifyPhone = false)
            }
        }
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val config = resolveConfig(intent)
        if (config == null) {
            serviceScope.launch {
                sendSessionError(
                    sessionId = intent.getStringExtra(WatchSessionIntents.EXTRA_SESSION_ID) ?: "unknown-session",
                    code = "invalid_config",
                    message = "Unable to parse session start payload.",
                    recoverable = false,
                )
            }
            stopSelf()
            return
        }

        startForeground(
            WatchNotification.NOTIFICATION_ID,
            WatchNotification.build(
                this,
                title = "SleepCare tracking",
                content = "Preparing watch sensor session for ${config.sessionId}",
            ),
        )

        serviceScope.launch {
            startSessionInternal(config)
        }
    }

    private fun handleStop(intent: Intent) {
        serviceScope.launch {
            stopSessionInternal(
                reason = intent.getStringExtra(WatchSessionIntents.EXTRA_REASON) ?: "phone_stop",
                notifyPhone = true,
            )
        }
    }

    private fun handleFlushPolicy(intent: Intent) {
        val sessionId = resolveSessionId(intent) ?: currentConfig?.sessionId ?: return
        val policy = resolveFlushPolicy(intent) ?: return
        currentConfig = currentConfig?.copy(sessionId = sessionId, flushPolicy = policy) ?: WatchSessionConfig(
            sessionId = sessionId,
            flushPolicy = policy,
        )
        WatchSessionStore.updateFlushPolicy(policy)
        serviceScope.launch {
            backend.updateFlushPolicy(policy)
        }
    }

    private fun handleBackfill(intent: Intent) {
        val sessionId = resolveSessionId(intent) ?: currentConfig?.sessionId ?: return
        val fromSampleSeq = resolveBackfillFrom(intent) ?: return
        serviceScope.launch {
            val samples = buffer.fromSequence(sessionId, fromSampleSeq)
            if (samples.isEmpty()) return@launch
            val batch = WatchHeartRateBatch(
                sessionId = sessionId,
                messageSequence = samples.maxOf { it.messageSequence },
                deliveryMode = "backfill",
                samples = samples,
            )
            messenger.send(WatchPaths.HrBatch, WatchSessionIntents.buildBatchPayload(batch))
        }
    }

    private fun handleAlert(intent: Intent) {
        val sessionId = resolveSessionId(intent) ?: currentConfig?.sessionId ?: return
        val level = intent.getIntExtra(WatchSessionIntents.EXTRA_LEVEL, 3)
        val pattern = intent.getStringExtra(WatchSessionIntents.EXTRA_PATTERN) ?: "pulse"
        val reason = intent.getStringExtra(WatchSessionIntents.EXTRA_REASON) ?: "Drowsiness risk"
        WatchSessionStore.markAlerting(
            badge = "${level * 30}% vigilance drop",
            body = reason,
        )
        vibrate(level, pattern)
    }

    private fun handleDismissAlert() {
        WatchSessionStore.dismissAlert()
    }

    private suspend fun startSessionInternal(config: WatchSessionConfig) {
        currentConfig = config
        trackingStarted = true
        WatchSessionStore.startDemoSession(config.sessionId)
        WatchSessionStore.updateFlushPolicy(config.flushPolicy)

        val startResult = backend.start(config) { sample ->
            serviceScope.launch {
                onSample(sample)
            }
        }

        if (!startResult.started) {
            sendSessionError(
                sessionId = config.sessionId,
                code = "backend_start_failed",
                message = startResult.message,
                recoverable = true,
            )
            WatchSessionStore.stopTracking(startResult.message)
            stopSelf()
            return
        }

        messenger.send(
            WatchPaths.SessionReady,
            WatchSessionIntents.buildSessionReadyPayload(config.sessionId),
        )
    }

    private suspend fun stopSessionInternal(
        reason: String,
        notifyPhone: Boolean,
    ) {
        val sessionId = currentConfig?.sessionId
        if (!trackingStarted && sessionId == null) {
            stopForegroundCompat()
            stopSelf()
            return
        }

        backend.stop()
        if (sessionId != null) {
            if (notifyPhone) {
                messenger.send(
                    WatchPaths.SessionClosed,
                    WatchSessionIntents.buildSessionClosedPayload(
                        sessionId = sessionId,
                        reason = reason,
                        finalSampleSeq = buffer.snapshot(sessionId).maxOfOrNull { it.sampleSeq },
                    ),
                )
            }
            buffer.clear(sessionId)
        }
        currentConfig = null
        trackingStarted = false
        WatchSessionStore.stopTracking(reason)
        stopForegroundCompat()
        stopSelf()
    }

    private suspend fun onSample(sample: WatchHeartRateSample) {
        buffer.append(sample)
        WatchSessionStore.updateHeartRate(sample)
        messenger.send(WatchPaths.HrLive, WatchSessionIntents.buildLiveSamplePayload(sample))
    }

    private suspend fun sendSessionError(
        sessionId: String,
        code: String,
        message: String,
        recoverable: Boolean,
    ) {
        messenger.send(
            WatchPaths.SessionError,
            WatchSessionIntents.buildSessionErrorPayload(sessionId, code, message, recoverable),
        )
    }

    private fun resolveConfig(intent: Intent): WatchSessionConfig? {
        WatchSessionIntents.decodeConfig(intent)?.let { return it }
        val sessionId = intent.getStringExtra(WatchSessionIntents.EXTRA_SESSION_ID) ?: return null
        val flushPolicy = resolveFlushPolicy(intent) ?: WatchFlushPolicy()
        return WatchSessionConfig(
            sessionId = sessionId,
            studyMode = intent.getStringExtra(WatchSessionIntents.EXTRA_STUDY_MODE) ?: "focus",
            flushPolicy = flushPolicy,
            hrRequired = intent.getBooleanExtra(WatchSessionIntents.EXTRA_HR_REQUIRED, true),
            watchVibrationEnabled = intent.getBooleanExtra(WatchSessionIntents.EXTRA_WATCH_VIBRATION_ENABLED, true),
        )
    }

    private fun resolveSessionId(intent: Intent): String? =
        WatchSessionIntents.decodeEnvelope(intent)?.sessionId
            ?: intent.getStringExtra(WatchSessionIntents.EXTRA_SESSION_ID)

    private fun resolveFlushPolicy(intent: Intent): WatchFlushPolicy? {
        WatchSessionIntents.decodeFlushPolicy(intent)?.let { return it }
        if (!intent.hasExtra(WatchSessionIntents.EXTRA_FLUSH_NORMAL_SEC)) return null
        return WatchFlushPolicy(
            normalSec = intent.getIntExtra(WatchSessionIntents.EXTRA_FLUSH_NORMAL_SEC, 15),
            suspectSec = intent.getIntExtra(WatchSessionIntents.EXTRA_FLUSH_SUSPECT_SEC, 5),
            alertSec = intent.getIntExtra(WatchSessionIntents.EXTRA_FLUSH_ALERT_SEC, 2),
        )
    }

    private fun resolveBackfillFrom(intent: Intent): Long? =
        WatchSessionIntents.decodeBackfillFrom(intent)
            ?: if (intent.hasExtra(WatchSessionIntents.EXTRA_FROM_SAMPLE_SEQ)) {
                intent.getLongExtra(WatchSessionIntents.EXTRA_FROM_SAMPLE_SEQ, -1L).takeIf { it >= 0L }
            } else {
                null
            }

    private fun vibrate(level: Int, pattern: String) {
        val vibrator = ContextCompat.getSystemService(this, Vibrator::class.java) ?: return
        val durationMs = when (pattern) {
            "pulse" -> 500L
            "urgent" -> 900L
            else -> 350L + (level.coerceIn(1, 5) * 100L)
        }
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                durationMs,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
        )
    }

    private fun stopForegroundCompat() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        fun start(context: Context, config: WatchSessionConfig) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.startSession(context, config))
        }

        fun startFromPhone(context: Context, rawMessage: ByteArray) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.startSessionFromPhone(context, rawMessage))
        }

        fun stop(context: Context, sessionId: String? = null, reason: String? = null) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.stopSession(context, sessionId, reason))
        }

        fun stopFromPhone(context: Context, rawMessage: ByteArray) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.stopSessionFromPhone(context, rawMessage))
        }

        fun updateFlushPolicy(context: Context, sessionId: String, flushPolicy: WatchFlushPolicy) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.updateFlushPolicy(context, sessionId, flushPolicy))
        }

        fun updateFlushPolicyFromPhone(context: Context, rawMessage: ByteArray) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.updateFlushPolicyFromPhone(context, rawMessage))
        }

        fun requestBackfill(context: Context, sessionId: String, fromSampleSeq: Long) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.requestBackfill(context, sessionId, fromSampleSeq))
        }

        fun requestBackfillFromPhone(context: Context, rawMessage: ByteArray) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.requestBackfillFromPhone(context, rawMessage))
        }

        fun triggerAlert(context: Context, sessionId: String, level: Int, pattern: String, reason: String) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.triggerAlert(context, sessionId, level, pattern, reason))
        }

        fun triggerAlertFromPhone(context: Context, rawMessage: ByteArray) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.triggerAlertFromPhone(context, rawMessage))
        }

        fun dismissAlert(context: Context) {
            WatchSessionIntents.startForegroundService(context, WatchSessionIntents.dismissAlert(context))
        }
    }
}
