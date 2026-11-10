package com.vaultlock.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.vaultlock.app.R

/**
 * An opaque window added the instant a protected app is detected, before the lock activity
 * has had time to start. Without it the app's content is visible for a few frames.
 * It is removed once the lock screen has drawn, when another app comes to the front,
 * or after a timeout so it can never trap the user.
 */
object LockCover {

    private const val TIMEOUT_MS = 2_500L

    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { hide() }
    private var view: View? = null
    private var windowManager: WindowManager? = null

    fun show(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { show(context) }
            return
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, TIMEOUT_MS)
        if (view != null) return
        val type = when {
            context is AccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Settings.canDrawOverlays(context) -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> return
        }
        val manager = context.getSystemService(WindowManager::class.java) ?: return
        val cover = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.brand_dark))
            isClickable = true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE,
        ).apply {
            windowAnimations = 0
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching {
            manager.addView(cover, params)
            view = cover
            windowManager = manager
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { hide() }
            return
        }
        main.removeCallbacks(timeout)
        val cover = view ?: return
        runCatching { windowManager?.removeViewImmediate(cover) }
        view = null
        windowManager = null
    }

    /** Hides the cover once [anchor] has drawn at least one frame on screen. */
    fun hideAfterDraw(anchor: View) {
        anchor.postDelayed({ hide() }, 120)
    }
}
