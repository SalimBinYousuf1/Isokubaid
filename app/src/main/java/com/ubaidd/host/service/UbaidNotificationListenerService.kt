package com.ubaidd.host.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class UbaidNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "UbaidNotificationSvc"

        @Volatile
        var instance: UbaidNotificationListenerService? = null
            private set

        val isBound: Boolean
            get() = instance != null

        @Volatile
        var totalNotificationsForwarded: Long = 0L
            private set

        private val listeners = mutableListOf<NotificationEventCallback>()

        fun registerCallback(callback: NotificationEventCallback) {
            synchronized(listeners) {
                if (!listeners.contains(callback)) {
                    listeners.add(callback)
                }
            }
        }

        fun unregisterCallback(callback: NotificationEventCallback) {
            synchronized(listeners) {
                listeners.remove(callback)
            }
        }
    }

    interface NotificationEventCallback {
        fun onNotificationPostedEvent(
            id: String,
            packageName: String,
            appName: String,
            title: String,
            text: String,
            postTime: Long
        )
        fun onNotificationRemovedEvent(id: String, packageName: String)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "NotificationListenerService connected and bound")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        Log.w(TAG, "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Skip our own persistent foreground notifications to prevent feedback loops
        if (pkg == packageName) return

        try {
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg
            }

            totalNotificationsForwarded++

            synchronized(listeners) {
                listeners.forEach {
                    it.onNotificationPostedEvent(
                        id = sbn.key ?: sbn.id.toString(),
                        packageName = pkg,
                        appName = appName,
                        title = title,
                        text = text,
                        postTime = sbn.postTime
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification post", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val key = sbn.key ?: sbn.id.toString()
        synchronized(listeners) {
            listeners.forEach {
                it.onNotificationRemovedEvent(key, pkg)
            }
        }
    }
}
