package com.vaultlock.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultlock.app.app
import com.vaultlock.app.lock.MonitorService
import com.vaultlock.app.security.CredentialActions
import com.vaultlock.app.ui.theme.Brand
import com.vaultlock.app.util.AppSession
import kotlinx.coroutines.launch

private enum class Step { WELCOME, LOCK, RECOVERY, PERMISSIONS }

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
        label = "onboarding",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { current ->
        when (current) {
            Step.WELCOME -> Welcome(onStart = { step = Step.LOCK })
            Step.LOCK -> LockSetupFlow(
                onSaved = { step = Step.RECOVERY },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
            Step.RECOVERY -> RecoveryStep(onContinue = { step = Step.PERMISSIONS })
            Step.PERMISSIONS -> Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PermissionsContent(
                    modifier = Modifier.weight(1f),
                    title = "Turn on protection",
                    subtitle = "VaultLock needs a few permissions to detect when a locked app opens and cover it instantly.",
                )
                Button(
                    onClick = {
                        scope.launch {
                            val updated = context.app.settings.edit { it.copy(setupComplete = true, protectionEnabled = true) }
                            AppSession.unlock()
                            MonitorService.sync(context, updated)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .height(56.dp),
                ) {
                    Text("Finish setup")
                }
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.lockBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brand.hero),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("VaultLock", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Keep your apps, photos and secrets private — with a lock that looks good and gets out of your way.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(32.dp))
        Feature(Icons.Rounded.Lock, "Lock any app", "PIN, pattern, fingerprint or face")
        Feature(Icons.Rounded.Key, "Encrypted vault", "Hide photos, videos, files and notes")
        Feature(Icons.Rounded.CameraFront, "Intruder selfies", "See who tried to get in")
        Feature(Icons.Rounded.VisibilityOff, "Stay invisible", "Disguise icon and fake crash screen")
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Get started", fontSize = 16.sp)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Brand.VioletLight)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun RecoveryStep(onContinue: () -> Unit, buttonText: String = "Continue") {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { code = CredentialActions.newRecoveryCode(context) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Save your recovery code", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "If you ever forget your PIN or pattern, this code is the only way back in. Write it down or keep it somewhere safe — it won't be shown again.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    code ?: "••••-••••-••••",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val value = code ?: return@OutlinedButton
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("VaultLock recovery code", value))
                        Toast.makeText(context, "Copied. Store it somewhere safe.", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy code")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { saved = !saved }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text("I've saved my recovery code", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            enabled = saved && code != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(buttonText)
        }
    }
}
