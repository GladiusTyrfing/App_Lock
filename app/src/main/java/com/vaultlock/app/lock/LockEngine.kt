package com.vaultlock.app.lock

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.data.RelockPolicy
import com.vaultlock.app.util.AppSession
import java.util.concurrent.ConcurrentHashMap

object LockEngine {

    private const val ACTIVE = Long.MAX_VALUE
    private const val MAX_FAILURES = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 5 * 60_000L

    val UNINSTALL_GUARD = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    private val transient = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.documentsui",
        "com.google.android.documentsui",
    )

    private val sessions = ConcurrentHashMap<String, Long>()

    @Volatile
    var currentPackage: String? = null
        private set

    @Volatile
    var lockScreenPackage: String? = null

    @Volatile
    private var lastLaunchPackage: String? = null

    @Volatile
    private var lastLaunchAt = 0L

    @Volatile
    private var failures = 0

    @Volatile
    private var lockouts = 0

    @Volatile
    private var lockoutUntil = 0L

    fun isProtected(packageName: String, settings: AppSettings): Boolean =
        packageName in settings.lockedApps || (settings.uninstallProtection && packageName in UNINSTALL_GUARD)

    fun onForeground(context: Context, packageName: String) {
        if (packageName == context.packageName || packageName in transient) return
        val settings = context.app.settings.current
        val previous = currentPackage
        if (previous != null && previous != packageName) onLeft(previous, settings)
        currentPackage = packageName
        evaluate(context, packageName, settings)
    }

    private fun evaluate(context: Context, packageName: String, settings: AppSettings) {
        if (!settings.setupComplete || !settings.protectionEnabled) return
        if (!isProtected(packageName, settings)) return
        if (isSessionValid(packageName)) {
            sessions[packageName] = ACTIVE
            return
        }
        sessions.remove(packageName)
        showLock(context, packageName)
    }

    private fun onLeft(packageName: String, settings: AppSettings) {
        when (settings.relockPolicy) {
            RelockPolicy.IMMEDIATELY -> sessions.remove(packageName)
            RelockPolicy.AFTER_TIMEOUT -> if (sessions[packageName] == ACTIVE) {
                sessions[packageName] = SystemClock.elapsedRealtime() + settings.relockMinutes * 60_000L
            }
            RelockPolicy.SCREEN_OFF -> Unit
        }
    }

    private fun isSessionValid(packageName: String): Boolean {
        val until = sessions[packageName] ?: return false
        return until == ACTIVE || until > SystemClock.elapsedRealtime()
    }

    private fun showLock(context: Context, packageName: String) {
        if (lockScreenPackage == packageName) return
        val now = SystemClock.elapsedRealtime()
        if (lastLaunchPackage == packageName && now - lastLaunchAt < 700) return
        lastLaunchPackage = packageName
        lastLaunchAt = now
        val intent = Intent(context, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PACKAGE, packageName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        runCatching { context.startActivity(intent) }
    }

    fun onUnlocked(packageName: String) {
        sessions[packageName] = ACTIVE
        currentPackage = packageName
        registerSuccess()
    }

    fun onLockDismissed(packageName: String) {
        if (currentPackage == packageName) currentPackage = null
    }

    fun onScreenOff() {
        sessions.clear()
        lastLaunchPackage = null
        AppSession.lock()
    }

    fun onScreenOn(context: Context) {
        val packageName = currentPackage ?: return
        evaluate(context, packageName, context.app.settings.current)
    }

    fun relockAll() {
        sessions.clear()
    }

    fun registerFailure(): Long {
        failures++
        if (failures >= MAX_FAILURES) {
            failures = 0
            lockouts++
            val duration = (BASE_LOCKOUT_MS shl (lockouts - 1).coerceAtMost(4)).coerceAtMost(MAX_LOCKOUT_MS)
            lockoutUntil = SystemClock.elapsedRealtime() + duration
        }
        return lockoutRemaining()
    }

    fun registerSuccess() {
        failures = 0
        lockouts = 0
        lockoutUntil = 0L
    }

    fun lockoutRemaining(): Long = (lockoutUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
}
