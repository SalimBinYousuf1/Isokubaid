package com.ubaidd.host.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ubaidd.host.MainActivity
import com.ubaidd.host.R
import com.ubaidd.host.data.storage.HostPreferences

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UbaidBootReceiver"
        private const val NOTIF_CHANNEL_BOOT = "ubaid_boot_channel"
        private const val NOTIF_ID_BOOT = 1004
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val intentAction = intent?.action ?: return

        if (intentAction == Intent.ACTION_BOOT_COMPLETED || intentAction == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Device boot completed detected. Inspecting host autostart...")
            val prefs = HostPreferences(context)

            if (prefs.isSetupCompleted && prefs.autoStartEngine) {
                // 1. Auto-start background WebRtcHostService for data channels (Control, Files, Notifications, Status)
                val serviceIntent = Intent(context, WebRtcHostService::class.java).apply {
                    action = WebRtcHostService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Log.i(TAG, "WebRtcHostService auto-started successfully following boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed auto-starting WebRtcHostService on boot", e)
                }

                // 2. Post mandatory user notice for one-tap video re-grant
                postBootReConsentNotice(context)
            }
        }
    }

    private fun postBootReConsentNotice(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_BOOT,
                "Ubaid Host Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when device reboot requires user tap to resume video streaming"
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL_BOOT)
            .setContentTitle("Ubaid Host Reconnected After Reboot")
            .setContentText("Touch, files, and notification channels are active. Tap to resume screen mirroring.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BOOT, notification)
    }
}
