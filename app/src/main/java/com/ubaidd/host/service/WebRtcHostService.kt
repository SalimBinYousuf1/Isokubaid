package com.ubaidd.host.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ubaidd.host.MainActivity
import com.ubaidd.host.R
import com.ubaidd.host.data.storage.HostPreferences
import com.ubaidd.host.webrtc.WebRtcHostManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebRtcHostService : Service() {

    companion object {
        private const val TAG = "UbaidHostService"
        const val CHANNEL_ID = "ubaid_host_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ubaidd.host.action.START"
        const val ACTION_STOP = "com.ubaidd.host.action.STOP"
        const val ACTION_ATTACH_VIDEO = "com.ubaidd.host.action.ATTACH_VIDEO"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        @Volatile
        var instance: WebRtcHostService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        // In-memory debug log buffer for live UI diagnostics viewer
        private val _debugLogs = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
        val debugLogs = _debugLogs.asStateFlow()

        fun addDebugLog(tag: String, message: String) {
            val entry = Pair(System.currentTimeMillis(), "[$tag] $message")
            _debugLogs.value = (_debugLogs.value + entry).takeLast(250)
        }
    }

    private val binder = LocalBinder()
    var hostManager: WebRtcHostManager? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): WebRtcHostService = this@WebRtcHostService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.i(TAG, "WebRtcHostService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopEngine()
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

        if (action == ACTION_ATTACH_VIDEO && projectionData != null) {
            startInForeground(hasProjection = true)
            hostManager?.attachScreenCapture(projectionData)
            addDebugLog("Service", "MediaProjection attached to existing host session")
            return START_STICKY
        }

        startInForeground(hasProjection = projectionData != null)
        startEngine(projectionData)

        return START_STICKY
    }

    private fun startInForeground(hasProjection: Boolean) {
        val notification = buildForegroundNotification(
            "Ubaid Host Engine Active",
            if (hasProjection) "Screen streaming and all 4 data channels active" else "Data channels active (Video standby)"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (hasProjection) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            }

            if (serviceType != 0) {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startEngine(projectionData: Intent?) {
        val prefs = HostPreferences(this)
        val pairingId = prefs.pairingId

        if (hostManager == null) {
            hostManager = WebRtcHostManager(
                context = this,
                pairingId = pairingId,
                onStreamConfirmedToast = { channelName ->
                    mainHandler.post {
                        Toast.makeText(applicationContext, "✓ Verified: $channelName", Toast.LENGTH_SHORT).show()
                    }
                },
                onLogEvent = { tag, message ->
                    addDebugLog(tag, message)
                }
            ).apply {
                onVideoConsentRequired = {
                    promptVideoConsentNotice()
                }
            }
        }

        hostManager?.startHost(projectionData)
        addDebugLog("Service", "Host Engine running for pairing ID: $pairingId")
    }

    private fun promptVideoConsentNotice() {
        startInForeground(hasProjection = false)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Standby")
            .setContentText("Tap to re-grant screen sharing consent. Data channels remain active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(1003, notification)
    }

    private fun stopEngine() {
        hostManager?.stopHost()
        hostManager = null
        addDebugLog("Service", "Host Engine stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubaid Host Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification keeping Ubaid remote host service alive"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateNotificationText(status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildForegroundNotification("Ubaid Host Engine", status)
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEngine()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "WebRtcHostService destroyed")
    }
}
