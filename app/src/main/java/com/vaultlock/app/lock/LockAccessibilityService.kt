package com.vaultlock.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class LockAccessibilityService : AccessibilityService() {

    private val activityCache = HashMap<String, Boolean>()

    // The monitor service can be killed and, on Android 12+, is not allowed to restart itself from
    // the background. Handling screen events here keeps re-locking working regardless.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> LockEngine.onScreenOff()
                Intent.ACTION_SCREEN_ON -> LockEngine.onScreenOn(context)
                Intent.ACTION_USER_PRESENT -> {
                    LockEngine.onScreenOn(context)
                    MonitorService.sync(context)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        MonitorService.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        if (!isActivity(packageName, className)) return
        LockEngine.onForeground(this, packageName)
    }

    private fun isActivity(packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        activityCache[key]?.let { return it }
        val result = try {
            packageManager.getActivityInfo(ComponentName(packageName, className), 0)
            true
        } catch (e: Exception) {
            false
        }
        if (activityCache.size > 500) activityCache.clear()
        activityCache[key] = result
        return result
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        LockCover.hide()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
            private set
    }
}
