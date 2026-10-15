package com.vaultlock.app.util

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSession {

    private const val GRACE_MS = 15_000L
    private const val EXTERNAL_GRACE_MS = 5 * 60_000L

    var unlocked by mutableStateOf(false)
        private set

    private var backgroundAt = 0L
    private var externalFlow = false

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun expectExternal() {
        externalFlow = true
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        if (backgroundAt == 0L) return
        val grace = if (externalFlow) EXTERNAL_GRACE_MS else GRACE_MS
        if (SystemClock.elapsedRealtime() - backgroundAt > grace) unlocked = false
        backgroundAt = 0L
        externalFlow = false
    }
}
