package com.vaultlock.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.apps.InstalledApp
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.ui.components.AppIcon
import com.vaultlock.app.ui.components.SectionHeader
import com.vaultlock.app.ui.components.SettingsGroup
import com.vaultlock.app.ui.components.SwitchRow
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenNotificationsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = context.app.settings
    val repo = context.app.apps
    val apps by repo.apps.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(Permissions.hasNotificationAccess(context)) }
    var canPost by remember { mutableStateOf(Permissions.hasNotifications(context)) }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }

    LaunchedEffect(Unit) { if (apps.isEmpty()) repo.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = Permissions.hasNotificationAccess(context)
        canPost = Permissions.hasNotifications(context)
    }

    // Keep the order stable while toggling: only re-sort when the list itself changes.
    val sorted = remember(apps) {
        val initial = settings.hiddenNotificationApps
        apps.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in initial }
                .thenByDescending { it.packageName in settings.lockedApps }
                .thenBy { it.label.lowercase() },
        )
    }

    fun toggle(packageName: String) {
        store.update {
            val hidden = if (packageName in it.hiddenNotificationApps) it.hiddenNotificationApps - packageName else it.hiddenNotificationApps + packageName
            it.copy(hiddenNotificationApps = hidden)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hide notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Notifications from the apps you pick are removed the moment they arrive and replaced with one that says only " +
                        "\"New notification\". Tap it to open the app (behind your lock if the app is locked).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (!hasAccess || !canPost) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                if (!hasAccess) "Notification access needed" else "Allow VaultLock notifications",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (!hasAccess) {
                                    "VaultLock needs notification access to hide them. On Android 13+ with a sideloaded APK, " +
                                        "first open App info → ⋮ → Allow restricted settings."
                                } else {
                                    "VaultLock needs to post the replacement notifications."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (!hasAccess) {
                                    TextButton(onClick = { context.launchExternal(Permissions.appDetailsIntent(context)) }) { Text("App info") }
                                    Spacer(Modifier.width(8.dp))
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (!hasAccess) {
                                            context.launchExternal(Permissions.notificationAccessIntent())
                                        } else if (Build.VERSION.SDK_INT >= 33) {
                                            AppSession.expectExternal()
                                            postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                ) { Text("Grant") }
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup {
                    SwitchRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Hide app name too",
                        subtitle = "Show only \"New notification\" without saying which app it came from",
                        checked = settings.hideNotificationAppName,
                        onCheckedChange = { value -> store.update { it.copy(hideNotificationAppName = value) } },
                    )
                }
                Text(
                    "Tip: turn off \"Pop on screen\" for these apps in system notification settings, " +
                        "so the original can't flash on screen before VaultLock removes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                SectionHeader("Apps · ${settings.hiddenNotificationApps.size} hidden")
            }
            if (loading && apps.isEmpty()) {
                item {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(sorted, key = { it.packageName }) { app ->
                val hidden = app.packageName in settings.hiddenNotificationApps
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { toggle(app.packageName) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName, 42.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (app.packageName in settings.lockedApps) {
                            Text("Locked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Switch(checked = hidden, onCheckedChange = { toggle(app.packageName) })
                }
            }
        }
    }
}
