package com.vaultlock.app.lock

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vaultlock.app.app
import com.vaultlock.app.ui.MainActivity
import kotlinx.coroutines.launch

class ProtectionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val settings = app.settings.current
        if (!settings.setupComplete) {
            openApp()
            return
        }
        if (settings.protectionEnabled) {
            if (isLocked) unlockAndRun { openApp() } else openApp()
        } else {
            app.scope.launch {
                val updated = app.settings.edit { it.copy(protectionEnabled = true) }
                MonitorService.sync(applicationContext, updated)
            }
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                updateTile()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = app.settings.current.protectionEnabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (enabled) "On" else "Paused"
        tile.updateTile()
    }
}
