package com.vaultlock.app.lock

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.ui.components.AuthPad
import com.vaultlock.app.ui.components.LockFrame
import com.vaultlock.app.ui.theme.LockScreenTheme
import com.vaultlock.app.util.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LockActivity : FragmentActivity() {

    private var target by mutableStateOf("")
    private var revealed by mutableStateOf(true)
    private var unlocked = false
    private var biometricShown = false
    private lateinit var camera: IntruderCamera

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        camera = IntruderCamera(this)
        if (!readIntent(intent)) return
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            if (settings.blockScreenshots) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val packageName = target
            val label by produceState(initialValue = "", packageName) {
                value = withContext(Dispatchers.IO) { app.apps.label(packageName) }
            }
            val icon by produceState<Bitmap?>(initialValue = null, packageName) {
                value = withContext(Dispatchers.IO) { app.apps.icon(packageName) }
            }
            LockScreenTheme {
                if (!revealed) {
                    FakeCrash(
                        label = label,
                        onClose = { goHome() },
                        onReveal = {
                            revealed = true
                            promptBiometric()
                        },
                    )
                } else {
                    LockFrame(title = label, icon = icon) {
                        AuthPad(
                            settings = settings,
                            contentColor = Color.White,
                            onVerified = { onUnlocked() },
                            onFailure = { attempts ->
                                if (settings.intruderEnabled) {
                                    if (attempts >= settings.intruderAttempts) camera.take(packageName) else camera.prepare()
                                }
                            },
                            onBiometric = if (settings.biometricEnabled && Biometrics.isAvailable(this@LockActivity)) {
                                { promptBiometric(force = true) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?): Boolean {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrEmpty()) {
            finish()
            return false
        }
        if (packageName != target) {
            target = packageName
            biometricShown = false
            camera.reset()
            revealed = !app.settings.current.fakeCrash
        }
        unlocked = false
        LockEngine.lockScreenPackage = packageName
        return true
    }

    override fun onResume() {
        super.onResume()
        LockEngine.lockScreenPackage = target
        promptBiometric()
    }

    private fun promptBiometric(force: Boolean = false) {
        val settings = app.settings.current
        if (!revealed || !settings.biometricEnabled || LockEngine.lockoutRemaining() > 0) return
        if (biometricShown && !force) return
        biometricShown = true
        Biometrics.prompt(
            activity = this,
            title = "Unlock ${app.apps.label(target)}",
            subtitle = null,
            onSuccess = { onUnlocked() },
        )
    }

    private fun onUnlocked() {
        if (unlocked) return
        unlocked = true
        LockEngine.onUnlocked(target)
        finish()
    }

    private fun goHome() {
        LockEngine.onLockDismissed(target)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!unlocked && !isChangingConfigurations && !isFinishing) finish()
    }

    override fun onDestroy() {
        if (LockEngine.lockScreenPackage == target) LockEngine.lockScreenPackage = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FakeCrash(label: String, onClose: () -> Unit, onReveal: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF2B2930),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    "$label keeps stopping",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE6E0E9),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                CrashOption(Icons.Rounded.Info, "App info", onClick = onClose, onLongClick = null)
                CrashOption(Icons.Rounded.Close, "Close app", onClick = onClose, onLongClick = onReveal)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrashOption(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFCAC4D0), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6E0E9))
    }
}
