package com.ubaidd.host.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubaidd.host.data.model.ChannelHealth
import com.ubaidd.host.data.model.ChannelState
import com.ubaidd.host.data.model.PairingConfig
import com.ubaidd.host.data.model.VideoMetrics
import com.ubaidd.host.data.storage.HostPreferences
import com.ubaidd.host.service.UbaidAccessibilityService
import com.ubaidd.host.service.UbaidNotificationListenerService
import com.ubaidd.host.service.WebRtcHostService
import com.ubaidd.host.webrtc.QrCodeGenerator
import com.ubaidd.host.webrtc.WebRtcHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class PermissionsState(
    val hasMediaProjectionConsent: Boolean = false,
    val isAccessibilityBound: Boolean = false,
    val isNotificationListenerBound: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
) {
    val allRequiredSatisfied: Boolean
        get() = hasMediaProjectionConsent &&
                isAccessibilityBound &&
                isNotificationListenerBound &&
                hasAllFilesAccess &&
                isBatteryOptimizationIgnored
}

class HostViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = HostPreferences(context)

    val pairingId: String get() = prefs.pairingId
    val deviceLabel: String get() = prefs.deviceLabel

    private val _isSetupCompleted = MutableStateFlow(prefs.isSetupCompleted)
    val isSetupCompleted = _isSetupCompleted.asStateFlow()

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState = _permissionsState.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(WebRtcHostService.isServiceRunning)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _pairingQrBitmap = MutableStateFlow<ImageBitmap?>(null)
    val pairingQrBitmap = _pairingQrBitmap.asStateFlow()

    // Captured MediaProjection Intent
    var cachedProjectionIntent: Intent? = null
        private set

    init {
        refreshSystemPermissions()
        generatePairingQrCode()
    }

    fun onMediaProjectionGranted(resultIntent: Intent) {
        cachedProjectionIntent = resultIntent
        _permissionsState.update { it.copy(hasMediaProjectionConsent = true) }
        refreshSystemPermissions()

        if (WebRtcHostService.isServiceRunning) {
            val serviceIntent = Intent(context, WebRtcHostService::class.java).apply {
                action = WebRtcHostService.ACTION_ATTACH_VIDEO
                putExtra(WebRtcHostService.EXTRA_PROJECTION_DATA, resultIntent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    fun refreshSystemPermissions() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val hasFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        val isBatteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val isAccessBound = UbaidAccessibilityService.isBound
        val isNotifBound = UbaidNotificationListenerService.isBound

        _permissionsState.update {
            it.copy(
                hasMediaProjectionConsent = cachedProjectionIntent != null || it.hasMediaProjectionConsent,
                isAccessibilityBound = isAccessBound,
                isNotificationListenerBound = isNotifBound,
                hasAllFilesAccess = hasFiles,
                isBatteryOptimizationIgnored = isBatteryIgnored
            )
        }

        _isServiceRunning.value = WebRtcHostService.isServiceRunning
    }

    fun markSetupCompleted() {
        prefs.isSetupCompleted = true
        _isSetupCompleted.value = true
    }

    fun resetSetupWizard() {
        prefs.isSetupCompleted = false
        _isSetupCompleted.value = false
        refreshSystemPermissions()
    }

    fun startHostEngine() {
        val serviceIntent = Intent(context, WebRtcHostService::class.java).apply {
            action = WebRtcHostService.ACTION_START
            if (cachedProjectionIntent != null) {
                putExtra(WebRtcHostService.EXTRA_PROJECTION_DATA, cachedProjectionIntent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        _isServiceRunning.value = true
    }

    fun stopHostEngine() {
        val serviceIntent = Intent(context, WebRtcHostService::class.java).apply {
            action = WebRtcHostService.ACTION_STOP
        }
        context.startService(serviceIntent)
        _isServiceRunning.value = false
    }

    fun runManualDiagnostics() {
        WebRtcHostService.instance?.hostManager?.runManualDiagnostics()
    }

    fun generatePairingQrCode() {
        viewModelScope.launch(Dispatchers.Default) {
            val payload = JSONObject().apply {
                put("version", 1)
                put("pairingId", pairingId)
                put("projectId", "salim-x-ubaid")
                put("storageBucket", "salim-x-ubaid.firebasestorage.app")
                put("apiKey", "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w")
                put("hostPackage", "com.ubaidd.host")
                put("deviceName", deviceLabel)
            }.toString()

            val bitmap = QrCodeGenerator.generateQrBitmap(payload, sizePx = 640)
            _pairingQrBitmap.value = bitmap
        }
    }
}
