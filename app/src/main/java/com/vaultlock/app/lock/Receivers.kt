package com.vaultlock.app.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.vaultlock.app.app

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MonitorService.sync(context)
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCK_APP) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        context.app.settings.update { it.copy(lockedApps = it.lockedApps + packageName) }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val ACTION_LOCK_APP = "com.vaultlock.app.action.LOCK_APP"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_NOTIFICATION = "notification"
    }
}
