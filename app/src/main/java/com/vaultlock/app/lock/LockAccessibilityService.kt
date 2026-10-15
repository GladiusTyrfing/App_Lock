package com.vaultlock.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    private val activityCache = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
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
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
        if (activityCache.size > 500) activityCache.clear()
        activityCache[key] = result
        return result
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
            private set
    }
}
