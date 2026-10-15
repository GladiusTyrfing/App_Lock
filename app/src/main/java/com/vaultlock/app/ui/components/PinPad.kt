package com.vaultlock.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultlock.app.util.Haptics

private val letters = mapOf(
    2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL", 6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ", 0 to "+",
)

@Composable
fun PinDots(
    length: Int,
    filled: Int,
    error: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(length) { index ->
            val active = index < filled
            val size by animateDpAsState(if (active) 16.dp else 12.dp, label = "dot")
            val tint by animateColorAsState(
                when {
                    error -> MaterialTheme.colorScheme.error
                    active -> color
                    else -> color.copy(alpha = 0.25f)
                },
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    randomized: Boolean,
    enabled: Boolean,
    vibration: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leftSlot: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val digits = remember(randomized) { if (randomized) (0..9).shuffled() else listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                for (col in 0 until 3) {
                    val digit = digits[row * 3 + col]
                    KeyButton(digit, if (randomized) null else letters[digit], enabled, contentColor) {
                        if (vibration) Haptics.tick(context)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { leftSlot() }
            KeyButton(digits[9], if (randomized) null else letters[digits[9]], enabled, contentColor) {
                if (vibration) Haptics.tick(context)
                onDigit(digits[9])
            }
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) {
                        if (vibration) Haptics.tick(context)
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Delete", tint = contentColor.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun KeyButton(digit: Int, sub: String?, enabled: Boolean, contentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = contentColor),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit.toString(), color = contentColor, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            if (sub != null) {
                Text(sub, color = contentColor.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            }
        }
    }
}
