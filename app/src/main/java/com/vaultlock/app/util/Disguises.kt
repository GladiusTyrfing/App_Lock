package com.vaultlock.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vaultlock.app.R
import com.vaultlock.app.data.Disguise

object Disguises {

    fun alias(disguise: Disguise): String = when (disguise) {
        Disguise.DEFAULT -> "LauncherDefault"
        Disguise.CALCULATOR -> "LauncherCalculator"
        Disguise.WEATHER -> "LauncherWeather"
        Disguise.CLOCK -> "LauncherClock"
        Disguise.NOTES -> "LauncherNotes"
    }

    fun label(disguise: Disguise): String = when (disguise) {
        Disguise.DEFAULT -> "VaultLock"
        Disguise.CALCULATOR -> "Calculator"
        Disguise.WEATHER -> "Weather"
        Disguise.CLOCK -> "Clock"
        Disguise.NOTES -> "Notes"
    }

    fun icon(disguise: Disguise): Int = when (disguise) {
        Disguise.DEFAULT -> R.mipmap.ic_launcher
        Disguise.CALCULATOR -> R.mipmap.ic_disguise_calculator
        Disguise.WEATHER -> R.mipmap.ic_disguise_weather
        Disguise.CLOCK -> R.mipmap.ic_disguise_clock
        Disguise.NOTES -> R.mipmap.ic_disguise_notes
    }

    fun apply(context: Context, selected: Disguise) {
        val pm = context.packageManager
        Disguise.entries.sortedBy { if (it == selected) 0 else 1 }.forEach { disguise ->
            val component = ComponentName(context.packageName, "com.vaultlock.app.${alias(disguise)}")
            val state = if (disguise == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
        }
    }
}
