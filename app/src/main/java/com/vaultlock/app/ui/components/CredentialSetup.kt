package com.vaultlock.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaultlock.app.data.LockType
import com.vaultlock.app.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CredentialSetup(
    type: LockType,
    pinLength: Int,
    contentColor: Color,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by remember(type, pinLength) { mutableStateOf<String?>(null) }
    var pin by remember(type, pinLength) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var clearSignal by remember { mutableIntStateOf(0) }
    val noun = if (type == LockType.PIN) "PIN" else "pattern"

    fun accept(secret: String) {
        val initial = first
        if (initial == null) {
            first = secret
            error = null
            pin = ""
            clearSignal++
        } else if (initial == secret) {
            Haptics.success(context)
            onComplete(secret)
        } else {
            Haptics.error(context)
            showError = true
            error = "That didn't match. Start again."
            scope.launch {
                delay(450)
                first = null
                pin = ""
                showError = false
                clearSignal++
            }
        }
    }

    val title = if (first == null) {
        if (type == LockType.PIN) "Create a $pinLength-digit PIN" else "Draw an unlock pattern"
    } else {
        "Confirm your $noun"
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AnimatedContent(targetState = title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") {
            Text(it, style = MaterialTheme.typography.titleLarge, color = contentColor, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: if (type == LockType.PATTERN && first == null) "Connect at least 4 dots" else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else contentColor.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(28.dp))
        when (type) {
            LockType.PIN -> {
                PinDots(pinLength, pin.length, showError, contentColor)
                Spacer(Modifier.height(36.dp))
                PinKeypad(
                    onDigit = { digit ->
                        if (pin.length < pinLength && !showError) {
                            pin += digit
                            if (pin.length == pinLength) {
                                val value = pin
                                scope.launch {
                                    delay(120)
                                    accept(value)
                                }
                            }
                        }
                    },
                    onDelete = { pin = pin.dropLast(1) },
                    randomized = false,
                    enabled = !showError,
                    vibration = true,
                    contentColor = contentColor,
                )
            }
            LockType.PATTERN -> PatternLock(
                onComplete = { cells ->
                    if (cells.size < 4) {
                        error = "Connect at least 4 dots"
                        showError = true
                        scope.launch {
                            delay(500)
                            showError = false
                            clearSignal++
                        }
                    } else {
                        scope.launch {
                            delay(150)
                            accept(cells.joinToString(""))
                        }
                    }
                },
                invisible = false,
                error = showError,
                enabled = !showError,
                vibration = true,
                color = contentColor,
                clearSignal = clearSignal,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}
