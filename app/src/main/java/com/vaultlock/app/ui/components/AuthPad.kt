package com.vaultlock.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.data.LockType
import com.vaultlock.app.lock.LockEngine
import com.vaultlock.app.security.Credentials
import com.vaultlock.app.util.Biometrics
import com.vaultlock.app.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun AuthPad(
    settings: AppSettings,
    contentColor: Color,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
    onFailure: (Int) -> Unit = {},
    onBiometric: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var clearSignal by remember { mutableIntStateOf(0) }
    var lockout by remember { mutableLongStateOf(LockEngine.lockoutRemaining()) }
    val shake = remember { Animatable(0f) }
    val locked = lockout > 0

    LaunchedEffect(locked) {
        while (LockEngine.lockoutRemaining() > 0) {
            lockout = LockEngine.lockoutRemaining()
            delay(250)
        }
        lockout = 0
    }

    fun submit(secret: String) {
        if (busy || locked) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                Credentials.verify(secret, settings.credentialSalt, settings.credentialHash)
            }
            if (ok) {
                if (settings.vibration) Haptics.success(context)
                LockEngine.registerSuccess()
                onVerified()
            } else {
                attempts++
                lockout = LockEngine.registerFailure()
                error = true
                if (settings.vibration) Haptics.error(context)
                onFailure(attempts)
                for (target in listOf(18f, -16f, 12f, -8f, 4f, 0f)) {
                    shake.animateTo(target, spring(stiffness = Spring.StiffnessHigh))
                }
                delay(250)
                pin = ""
                clearSignal++
                error = false
            }
            busy = false
        }
    }

    val message = when {
        locked -> "Too many attempts. Try again in ${(lockout + 999) / 1000}s"
        error && settings.lockType == LockType.PIN -> "Incorrect PIN"
        error -> "Incorrect pattern"
        settings.lockType == LockType.PIN -> "Enter your PIN"
        else -> "Draw your unlock pattern"
    }

    val biometricIcon = if (Biometrics.hasFace(context) && !Biometrics.hasFingerprint(context)) Icons.Rounded.Face else Icons.Rounded.Fingerprint

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(targetState = message, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "message") { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (error || locked) MaterialTheme.colorScheme.error else contentColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        when (settings.lockType) {
            LockType.PIN -> {
                PinDots(
                    length = settings.pinLength,
                    filled = pin.length,
                    error = error,
                    color = contentColor,
                    modifier = Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                )
                Spacer(Modifier.height(40.dp))
                PinKeypad(
                    onDigit = { digit ->
                        if (pin.length < settings.pinLength && !busy) {
                            pin += digit
                            if (pin.length == settings.pinLength) submit(pin)
                        }
                    },
                    onDelete = { if (!busy) pin = pin.dropLast(1) },
                    randomized = settings.randomKeypad,
                    enabled = !locked && !busy,
                    vibration = settings.vibration,
                    contentColor = contentColor,
                    leftSlot = {
                        if (onBiometric != null) {
                            FilledTonalIconButton(
                                onClick = onBiometric,
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(biometricIcon, contentDescription = "Biometric unlock", modifier = Modifier.size(32.dp))
                            }
                        }
                    },
                )
            }
            LockType.PATTERN -> {
                PatternLock(
                    onComplete = { cells ->
                        if (cells.size < 4) {
                            error = true
                            scope.launch {
                                delay(500)
                                error = false
                                clearSignal++
                            }
                        } else {
                            submit(cells.joinToString(""))
                        }
                    },
                    invisible = settings.invisiblePattern,
                    error = error,
                    enabled = !locked && !busy,
                    vibration = settings.vibration,
                    color = contentColor,
                    clearSignal = clearSignal,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                )
                if (onBiometric != null) {
                    Spacer(Modifier.height(24.dp))
                    FilledTonalIconButton(
                        onClick = onBiometric,
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(biometricIcon, contentDescription = "Biometric unlock", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (attempts > 0 && !locked) {
            Text(
                "$attempts failed ${if (attempts == 1) "attempt" else "attempts"}",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.5f),
            )
        }
    }
}
