package com.ubaidd.host.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ubaidd.host.MainActivity
import com.ubaidd.host.R
import com.ubaidd.host.data.storage.HostPreferences

class HostWatchdogWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UbaidWatchdog"
        const val ALERT_CHANNEL_ID = "ubaid_watchdog_alerts"
        const val ALERT_NOTIF_ID = 1002
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Watchdog health check executing...")
        val prefs = HostPreferences(context)

        if (!prefs.isSetupCompleted || !prefs.autoStartEngine) {
            return Result.success()
        }

        if (!WebRtcHostService.isServiceRunning) {
            Log.w(TAG, "Watchdog detected service is not running. Initiating auto-recovery...")
            val serviceIntent = Intent(context, WebRtcHostService::class.java).apply {
                action = WebRtcHostService.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Watchdog failed to restart foreground service", e)
            }

            // Prompt user if screen capture permission is required
            postVideoReConsentNotice()
        }

        return Result.success()
    }

    private fun postVideoReConsentNotice() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Ubaid Watchdog Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when screen capture permission requires user tap"
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ubaid Screen Capture Standby")
            .setContentText("Background recovery active. Tap to resume live screen mirroring.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(ALERT_NOTIF_ID, notification)
    }
}
