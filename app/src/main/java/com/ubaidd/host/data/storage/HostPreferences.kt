package com.ubaidd.host.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class HostPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ubaid_host_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PAIRING_ID = "key_pairing_id"
        private const val KEY_SETUP_COMPLETED = "key_setup_completed"
        private const val KEY_DEVICE_LABEL = "key_device_label"
        private const val KEY_AUTO_START = "key_auto_start"
    }

    var pairingId: String
        get() {
            var id = prefs.getString(KEY_PAIRING_ID, null)
            if (id.isNullOrBlank()) {
                val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
                id = "UBD-$randomSuffix"
                prefs.edit().putString(KEY_PAIRING_ID, id).apply()
            }
            return id
        }
        set(value) {
            prefs.edit().putString(KEY_PAIRING_ID, value).apply()
        }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var deviceLabel: String
        get() {
            return prefs.getString(
                KEY_DEVICE_LABEL,
                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            ) ?: "Ubaid Host"
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_LABEL, value).apply()

    var autoStartEngine: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()
}
