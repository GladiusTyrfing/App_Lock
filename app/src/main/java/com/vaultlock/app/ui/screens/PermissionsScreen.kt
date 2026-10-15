package com.vaultlock.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vaultlock.app.lock.MonitorService
import com.vaultlock.app.ui.components.IconBubble
import com.vaultlock.app.ui.theme.Brand
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.util.Permissions

private data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val granted: Boolean,
    val badge: String?,
    val action: () -> Unit,
    val secondary: Pair<String, () -> Unit>? = null,
)

fun Context.launchExternal(intent: Intent) {
    AppSession.expectExternal()
    runCatching { startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        PermissionsContent(modifier = Modifier.padding(padding), title = null, subtitle = null)
    }
}

@Composable
fun PermissionsContent(modifier: Modifier = Modifier, title: String?, subtitle: String?) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        MonitorService.sync(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val items = remember(refresh) {
        val accessibility = Permissions.isAccessibilityEnabled(context)
        buildList {
            add(
                PermissionItem(
                    icon = Icons.Rounded.Accessibility,
                    title = "Instant app detection",
                    description = "Accessibility service that spots a locked app the moment it opens. It never reads screen content. On Android 13+ you may need App info → ⋮ → Allow restricted settings first.",
                    granted = accessibility,
                    badge = "Recommended",
                    action = { context.launchExternal(Permissions.accessibilityIntent()) },
                    secondary = "App info" to { context.launchExternal(Permissions.appDetailsIntent(context)) },
                ),
            )
            add(
                PermissionItem(
                    icon = Icons.Rounded.DataUsage,
                    title = "Usage access",
                    description = "Backup detection used when the accessibility service is off.",
                    granted = Permissions.hasUsageAccess(context),
                    badge = if (accessibility) "Optional" else "Required",
                    action = { context.launchExternal(Permissions.usageAccessIntent()) },
                ),
            )
            add(
                PermissionItem(
                    icon = Icons.Rounded.Layers,
                    title = "Display over other apps",
                    description = "Lets the lock screen appear on top of protected apps.",
                    granted = Permissions.canDrawOverlays(context),
                    badge = "Required",
                    action = { context.launchExternal(Permissions.overlayIntent(context)) },
                ),
            )
            if (Build.VERSION.SDK_INT >= 33) {
                add(
                    PermissionItem(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        description = "Keeps protection alive and suggests locking newly installed apps.",
                        granted = Permissions.hasNotifications(context),
                        badge = "Recommended",
                        action = {
                            AppSession.expectExternal()
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    ),
                )
            }
            add(
                PermissionItem(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Camera",
                    description = "Takes an intruder selfie after wrong unlock attempts.",
                    granted = Permissions.hasCamera(context),
                    badge = "Optional",
                    action = {
                        AppSession.expectExternal()
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    },
                ),
            )
            add(
                PermissionItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Unrestricted battery",
                    description = "Stops the system from killing protection in the background.",
                    granted = Permissions.ignoresBatteryOptimizations(context),
                    badge = "Recommended",
                    action = { context.launchExternal(Permissions.batteryIntent(context)) },
                ),
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(items.size) { index -> PermissionCard(items[index]) }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(item.icon, if (item.granted) Brand.Mint else MaterialTheme.colorScheme.primary, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.badge != null) {
                        Text(
                            item.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.badge == "Required") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.granted) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = Brand.Mint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!item.granted) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    item.secondary?.let { (label, action) ->
                        TextButton(onClick = action) { Text(label) }
                        Spacer(Modifier.width(8.dp))
                    }
                    FilledTonalButton(onClick = item.action) { Text("Grant") }
                }
            }
        }
    }
}

