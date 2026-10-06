package com.ubaidd.host.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import com.ubaidd.host.data.model.DeviceTelemetry
import org.json.JSONObject

class StatusReporter(
    private val context: Context,
    private val onTelemetryChanged: ((DeviceTelemetry) -> Unit)? = null
) {
    companion object {
        private const val TAG = "UbaidStatusReporter"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var lastTelemetry = DeviceTelemetry()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val current = sampleCurrentTelemetry()
                if (current != lastTelemetry) {
                    lastTelemetry = current
                    onTelemetryChanged?.invoke(current)
                }
            }
        }
    }

    fun start() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            lastTelemetry = sampleCurrentTelemetry()
            onTelemetryChanged?.invoke(lastTelemetry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver", e)
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
    }

    fun sampleCurrentTelemetry(): DeviceTelemetry {
        var batteryPct = 100
        var isCharging = false
        var powerSource = "Battery"

        try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }

            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                }

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                powerSource = when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> if (isCharging) "Charging" else "Battery"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sampling battery status", e)
        }

        val networkType = determineNetworkType()
        val isScreenOn = powerManager?.isInteractive ?: true

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            powerSource = powerSource,
            networkType = networkType,
            isScreenOn = isScreenOn,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun determineNetworkType(): String {
        return try {
            val cm = connectivityManager ?: return "Unknown"
            val activeNetwork = cm.activeNetwork ?: return "Disconnected"
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Disconnected"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun buildTelemetryJson(telemetry: DeviceTelemetry = sampleCurrentTelemetry()): String {
        return JSONObject().apply {
            put("type", "status_update")
            put("batteryPct", telemetry.batteryPercent)
            put("isCharging", telemetry.isCharging)
            put("powerSource", telemetry.powerSource)
            put("networkType", telemetry.networkType)
            put("isScreenOn", telemetry.isScreenOn)
            put("timestamp", telemetry.timestamp)
        }.toString()
    }

    fun handlePing(requestJsonStr: String): String {
        val json = try { JSONObject(requestJsonStr) } catch (e: Exception) { JSONObject() }
        val telemetry = sampleCurrentTelemetry()
        return JSONObject().apply {
            put("type", "pong")
            put("timestamp", json.optLong("timestamp", System.currentTimeMillis()))
            put("status", "active")
            put("batteryPct", telemetry.batteryPercent)
            put("isCharging", telemetry.isCharging)
            put("networkType", telemetry.networkType)
        }.toString()
    }
}
