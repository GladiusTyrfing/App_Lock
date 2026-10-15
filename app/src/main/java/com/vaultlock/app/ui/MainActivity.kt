package com.vaultlock.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.lock.MonitorService
import com.vaultlock.app.ui.screens.MainScaffold
import com.vaultlock.app.ui.screens.OnboardingScreen
import com.vaultlock.app.ui.screens.UnlockScreen
import com.vaultlock.app.ui.theme.VaultLockTheme
import com.vaultlock.app.util.AppSession

class MainActivity : FragmentActivity() {

    private enum class Stage { ONBOARDING, LOCKED, READY }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            VaultLockTheme(settings) {
                val stage = when {
                    !settings.setupComplete -> Stage.ONBOARDING
                    !AppSession.unlocked -> Stage.LOCKED
                    else -> Stage.READY
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Crossfade(targetState = stage, label = "stage") { current ->
                        when (current) {
                            Stage.ONBOARDING -> OnboardingScreen()
                            Stage.LOCKED -> UnlockScreen(settings)
                            Stage.READY -> MainScaffold(settings)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppSession.onForeground()
    }

    override fun onResume() {
        super.onResume()
        MonitorService.sync(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppSession.onBackground()
    }
}
