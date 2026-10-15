package com.vaultlock.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.lock.LockEngine
import com.vaultlock.app.security.CredentialActions
import com.vaultlock.app.ui.components.AuthPad
import com.vaultlock.app.ui.components.LockFrame
import com.vaultlock.app.ui.theme.LockScreenTheme
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.util.Biometrics
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(settings: AppSettings) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    var showRecovery by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val canBiometric = settings.biometricEnabled && Biometrics.isAvailable(context)

    fun biometric() {
        Biometrics.prompt(activity, "Unlock VaultLock", null, onSuccess = { AppSession.unlock() })
    }

    LaunchedEffect(Unit) {
        if (canBiometric && LockEngine.lockoutRemaining() == 0L) biometric()
    }

    if (resetting) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LockSetupFlow(
                header = "Set a new lock",
                onSaved = { AppSession.unlock() },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
        }
        return
    }

    LockScreenTheme {
        LockFrame(
            title = "VaultLock",
            icon = null,
            overlay = {
                TextButton(
                    onClick = { showRecovery = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    Text("Forgot your ${if (settings.lockType.name == "PIN") "PIN" else "pattern"}?", color = Color.White.copy(alpha = 0.7f))
                }
            },
        ) {
            AuthPad(
                settings = settings,
                contentColor = Color.White,
                onVerified = { AppSession.unlock() },
                onBiometric = if (canBiometric) ({ biometric() }) else null,
            )
        }
        if (showRecovery) {
            RecoveryDialog(
                settings = settings,
                onDismiss = { showRecovery = false },
                onVerified = {
                    showRecovery = false
                    resetting = true
                },
            )
        }
    }
}

@Composable
private fun RecoveryDialog(settings: AppSettings, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use recovery code") },
        text = {
            Box {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        error = false
                    },
                    label = { Text("Recovery code") },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text("That code isn't right") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank() && !checking && settings.recoveryHash.isNotEmpty(),
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = CredentialActions.verifyRecovery(settings, code)
                        checking = false
                        if (ok) onVerified() else {
                            error = true
                            LockEngine.registerFailure()
                        }
                    }
                },
            ) { Text("Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
