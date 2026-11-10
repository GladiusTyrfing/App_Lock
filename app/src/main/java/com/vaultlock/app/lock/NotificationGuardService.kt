package com.vaultlock.app.lock

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Android does not let other apps blur or edit notifications, so for every app the user picked
 * this removes its notifications as they arrive and posts one content-free stand-in instead.
 * Tapping the stand-in opens the app (through the lock screen when the app is locked).
 */
class NotificationGuardService : NotificationListenerService() {

    private var scope: CoroutineScope? = null
    private val hiddenKeys = HashMap<String, MutableSet<String>>()
    private val lastIntent = HashMap<String, PendingIntent?>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope ->
            scope.launch {
                app.settings.state
                    .distinctUntilChangedBy { it.hiddenNotificationApps to it.hideNotificationAppName }
                    .collect { settings -> sweep(settings) }
            }
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        scope?.cancel()
        scope = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected = false
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        guard(sbn, app.settings.current, alert = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != packageName) return
        val tag = sbn.tag ?: return
        if (sbn.id != Notifications.ID_HIDDEN || !tag.startsWith(Notifications.TAG_HIDDEN_PREFIX)) return
        val target = tag.removePrefix(Notifications.TAG_HIDDEN_PREFIX)
        hiddenKeys.remove(target)
        lastIntent.remove(target)
    }

    private fun sweep(settings: AppSettings) {
        runCatching { activeNotifications }.getOrNull()?.forEach { guard(it, settings, alert = false) }
        hiddenKeys.keys.toList().forEach { target ->
            if (target in settings.hiddenNotificationApps) {
                post(target, settings)
            } else {
                // The app is no longer hidden, so its stand-in is no longer needed.
                hiddenKeys.remove(target)
                lastIntent.remove(target)
                runCatching {
                    NotificationManagerCompat.from(this).cancel(Notifications.TAG_HIDDEN_PREFIX + target, Notifications.ID_HIDDEN)
                }
            }
        }
    }

    private fun guard(sbn: StatusBarNotification, settings: AppSettings, alert: Boolean) {
        val target = sbn.packageName
        if (target == packageName || target !in settings.hiddenNotificationApps) return
        if (!sbn.isClearable) return
        runCatching { cancelNotification(sbn.key) }
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val keys = hiddenKeys.getOrPut(target) { mutableSetOf() }
        if (!keys.add(sbn.key) && !alert) return
        notification.contentIntent?.let { lastIntent[target] = it }
        post(target, settings)
    }

    private fun post(target: String, settings: AppSettings) {
        Notifications.hidden(
            context = this,
            packageName = target,
            label = app.apps.label(target),
            count = hiddenKeys[target]?.size ?: return,
            hideName = settings.hideNotificationAppName,
            contentIntent = lastIntent[target],
        )
    }

    companion object {
        @Volatile
        var connected = false
            private set
    }
}
