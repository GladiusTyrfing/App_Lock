package com.vaultlock.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vaultlock.app.util.Haptics

@Composable
fun PatternLock(
    onComplete: (List<Int>) -> Unit,
    invisible: Boolean,
    error: Boolean,
    enabled: Boolean,
    vibration: Boolean,
    color: Color,
    clearSignal: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val complete by rememberUpdatedState(onComplete)
    val haptics by rememberUpdatedState(vibration)
    val errorColor = MaterialTheme.colorScheme.error

    LaunchedEffect(clearSignal) {
        selected.clear()
        finger = null
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selected.clear()
                    val cellW = size.width / 3f
                    val cellH = size.height / 3f
                    fun hit(point: Offset) {
                        val col = (point.x / cellW).toInt()
                        val row = (point.y / cellH).toInt()
                        if (col !in 0..2 || row !in 0..2) return
                        val center = Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                        if ((point - center).getDistance() > cellW * 0.34f) return
                        val index = row * 3 + col
                        if (index in selected) return
                        val last = selected.lastOrNull()
                        if (last != null) {
                            val lastRow = last / 3
                            val lastCol = last % 3
                            if ((lastRow + row) % 2 == 0 && (lastCol + col) % 2 == 0) {
                                val middle = ((lastRow + row) / 2) * 3 + (lastCol + col) / 2
                                if (middle !in selected) selected.add(middle)
                            }
                        }
                        selected.add(index)
                        if (haptics) Haptics.tick(context)
                    }
                    finger = down.position
                    hit(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        finger = change.position
                        hit(change.position)
                        change.consume()
                    }
                    finger = null
                    if (selected.isNotEmpty()) complete(selected.toList())
                }
            },
    ) {
        val cellW = size.width / 3f
        val cellH = size.height / 3f
        val centers = (0 until 9).map { Offset((it % 3 + 0.5f) * cellW, (it / 3 + 0.5f) * cellH) }
        val active = if (error) errorColor else color
        val showTrail = !invisible || error
        if (showTrail && selected.isNotEmpty()) {
            for (i in 1 until selected.size) {
                drawLine(active.copy(alpha = 0.55f), centers[selected[i - 1]], centers[selected[i]], 6.dp.toPx(), StrokeCap.Round)
            }
            finger?.let { drawLine(active.copy(alpha = 0.35f), centers[selected.last()], it, 6.dp.toPx(), StrokeCap.Round) }
        }
        centers.forEachIndexed { index, center ->
            val isSelected = showTrail && index in selected
            if (isSelected) {
                drawCircle(active.copy(alpha = 0.16f), radius = 26.dp.toPx(), center = center)
                drawCircle(active, radius = 10.dp.toPx(), center = center)
            } else {
                drawCircle(color.copy(alpha = 0.12f), radius = 22.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                drawCircle(color.copy(alpha = 0.6f), radius = 6.dp.toPx(), center = center)
            }
        }
    }
}
