package com.vaultlock.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vaultlock.app.app
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.vault.VaultItem
import com.vaultlock.app.vault.VaultKind
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ViewerScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = context.app.vault
    val all by vault.items.collectAsStateWithLifecycle()
    val kind = remember(id) { all.firstOrNull { it.id == id }?.kind ?: VaultKind.PHOTO }
    val items = remember(all, kind) { all.filter { it.kind == kind } }
    val start = remember(id) { items.indexOfFirst { it.id == id }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onBack() }
    if (items.isEmpty()) return
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch { vault.export(listOf(current), tree) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { items[it].id }) { page ->
            val item = items[page]
            when (item.kind) {
                VaultKind.VIDEO -> VideoPage(item, active = pager.settledPage == page, onTap = { chrome = !chrome })
                else -> PhotoPage(item, onTap = { chrome = !chrome }, onZoomChanged = { if (pager.settledPage == page) zoomed = it })
            }
        }
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text(
                    current.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    AppSession.expectExternal()
                    exportLauncher.launch(null)
                }) { Icon(Icons.Rounded.SaveAlt, contentDescription = "Export", tint = Color.White) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from vault?") },
            text = { Text("\"${current.name}\" will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { vault.delete(setOf(current.id)) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhotoPage(item: VaultItem, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, item.id) { value = context.app.vault.loadImage(item, 2560) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale > 1f) { onZoomChanged(scale > 1f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                image.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || scale > 1f) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(item: VaultItem, active: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val vault = context.app.vault
    val file by produceState<File?>(null, item.id) { value = runCatching { vault.decryptToShared(item) }.getOrNull() }

    DisposableEffect(item.id) { onDispose { vault.clearShared(item) } }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        val source = file
        if (source == null) {
            Thumbnail(item, Modifier.fillMaxSize())
            CircularProgressIndicator(color = Color.White)
        } else if (active) {
            val player = remember(source) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(source)))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Thumbnail(item, Modifier.fillMaxSize())
        }
    }
}
