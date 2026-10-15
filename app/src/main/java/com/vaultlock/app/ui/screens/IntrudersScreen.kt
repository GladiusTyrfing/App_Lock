package com.vaultlock.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.lock.IntruderRecord
import com.vaultlock.app.ui.components.AppIcon
import com.vaultlock.app.ui.components.EmptyState
import com.vaultlock.app.ui.components.IconBubble
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.util.Permissions
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntrudersScreen(settings: AppSettings, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.intruders
    val records by repo.records.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<IntruderRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) context.app.settings.update { it.copy(intruderEnabled = true) }
    }

    LaunchedEffect(records.firstOrNull()?.timestamp) {
        val latest = records.firstOrNull()?.timestamp ?: return@LaunchedEffect
        if (latest > settings.intrudersSeenAt) context.app.settings.update { it.copy(intrudersSeenAt = latest) }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !Permissions.hasCamera(context)) {
            AppSession.expectExternal()
            cameraLauncher.launch(Manifest.permission.CAMERA)
        } else {
            context.app.settings.update { it.copy(intruderEnabled = enabled) }
        }
    }

    Scaffold(
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        topBar = {
            TopAppBar(
                title = { Text("Intruders", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear all") }
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.CameraFront, MaterialTheme.colorScheme.tertiary, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Intruder selfie", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Silently snap the front camera after wrong attempts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = settings.intruderEnabled && Permissions.hasCamera(context), onCheckedChange = ::setEnabled)
                        }
                        if (settings.intruderEnabled) {
                            Spacer(Modifier.height(14.dp))
                            Text("Capture after", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..4).forEach { count ->
                                    FilterChip(
                                        selected = settings.intruderAttempts == count,
                                        onClick = { context.app.settings.update { it.copy(intruderAttempts = count) } },
                                        label = { Text("$count") },
                                    )
                                }
                            }
                            Text(
                                "wrong ${if (settings.intruderAttempts == 1) "attempt" else "attempts"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (records.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Rounded.PersonSearch,
                        title = "No break-in attempts",
                        message = if (settings.intruderEnabled) {
                            "When someone enters the wrong PIN or pattern, their photo shows up here."
                        } else {
                            "Turn on intruder selfie to see who tries to open your locked apps."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(records, key = { it.fileName }) { record ->
                IntruderCard(record, onClick = { viewing = record })
            }
        }
    }

    viewing?.let { record ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.padding(24.dp)) {
                Column {
                    IntruderPhoto(record, 1600, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(record.packageName, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(context.app.apps.label(record.packageName), style = MaterialTheme.typography.titleMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            repo.delete(record)
                            viewing = null
                        }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all intruder photos?") },
            confirmButton = {
                TextButton(onClick = {
                    repo.clear()
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IntruderCard(record: IntruderRecord, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        IntruderPhoto(record, 480, Modifier.fillMaxWidth().aspectRatio(1f))
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(record.packageName, 24.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    context.app.apps.label(record.packageName),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(record.timestamp).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IntruderPhoto(record: IntruderRecord, size: Int, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, record.fileName, size) { value = context.app.intruders.load(record, size) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Image(image.asImageBitmap(), contentDescription = "Intruder photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
