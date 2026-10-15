package com.vaultlock.app.lock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vaultlock.app.app
import com.vaultlock.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : LifecycleService() {

    private var pollJob: Job? = null
    private var lastPolled: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LockEngine.onScreenOff()
                    stopPolling()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    LockEngine.onScreenOn(context)
                    startPolling()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val packageName = intent.data?.schemeSpecificPart ?: return
            val settings = context.app.settings.current
            if (!settings.promptNewApps || !settings.setupComplete) return
            if (packageName in settings.lockedApps) return
            if (!context.app.apps.isLaunchable(packageName)) return
            Notifications.newApp(context, packageName, context.app.apps.label(packageName))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground(app.settings.current.lockedApps.size)
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
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            app.settings.state.collect { settings ->
                if (!settings.protectionEnabled || !settings.setupComplete) {
                    stopSelf()
                } else {
                    startInForeground(settings.lockedApps.size)
                }
            }
        }
        if (getSystemService(PowerManager::class.java).isInteractive) startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startInForeground(count: Int) {
        val notification = Notifications.service(this, count, Permissions.detectionReady(this))
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, Notifications.ID_SERVICE, notification, type) }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = lifecycleScope.launch(Dispatchers.Default) {
            val usage = getSystemService(UsageStatsManager::class.java)
            while (isActive) {
                if (!LockAccessibilityService.running && Permissions.hasUsageAccess(this@MonitorService)) {
                    val current = foregroundPackage(usage)
                    if (current != null && current != lastPolled) {
                        lastPolled = current
                        launch(Dispatchers.Main) { LockEngine.onForeground(this@MonitorService, current) }
                    }
                }
                delay(if (LockAccessibilityService.running) 2_000L else 250L)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        lastPolled = null
    }

    @Suppress("DEPRECATION")
    private fun foregroundPackage(usage: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 10_000, now) ?: return null
        val event = UsageEvents.Event()
        var result: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) result = event.packageName
        }
        return result
    }

    override fun onDestroy() {
        stopPolling()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    companion object {
        fun sync(context: Context) {
            val settings = context.app.settings.current
            val intent = Intent(context, MonitorService::class.java)
            if (settings.setupComplete && settings.protectionEnabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
