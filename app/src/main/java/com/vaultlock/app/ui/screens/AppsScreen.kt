package com.vaultlock.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.apps.InstalledApp
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.lock.LockEngine
import com.vaultlock.app.lock.MonitorService
import com.vaultlock.app.ui.components.AppIcon
import com.vaultlock.app.ui.components.EmptyState
import com.vaultlock.app.ui.theme.Brand
import com.vaultlock.app.util.Permissions
import kotlinx.coroutines.launch

private enum class AppFilter(val label: String) { ALL("All"), LOCKED("Locked"), SUGGESTED("Suggested"), SYSTEM("System") }

@Composable
fun AppsScreen(settings: AppSettings, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = context.app.apps
    val apps by repo.apps.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var ready by remember { mutableStateOf(Permissions.fullyReady(context)) }

    LaunchedEffect(Unit) { repo.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ready = Permissions.fullyReady(context) }

    val visible = remember(apps, query, filter, settings.lockedApps) {
        apps.filter { app ->
            val matches = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matches && when (filter) {
                AppFilter.ALL -> true
                AppFilter.LOCKED -> app.packageName in settings.lockedApps
                AppFilter.SUGGESTED -> app.isSuggested
                AppFilter.SYSTEM -> app.isSystem
            }
        }
    }

    fun toggle(packageName: String) {
        context.app.settings.update {
            val locked = if (packageName in it.lockedApps) it.lockedApps - packageName else it.lockedApps + packageName
            it.copy(lockedApps = locked)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
                Text("VaultLock", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Choose which apps need your PIN, pattern or fingerprint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ProtectionCard(
                settings = settings,
                ready = ready,
                onToggle = { enabled ->
                    scope.launch {
                        val updated = context.app.settings.edit { it.copy(protectionEnabled = enabled) }
                        if (!enabled) LockEngine.relockAll()
                        MonitorService.sync(context, updated)
                    }
                },
                onFix = onOpenPermissions,
            )
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilter.entries.forEach { option ->
                    val count = when (option) {
                        AppFilter.LOCKED -> apps.count { it.packageName in settings.lockedApps }
                        else -> null
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(if (count != null) "${option.label} · $count" else option.label) },
                        shape = CircleShape,
                    )
                }
            }
        }
        if (loading) {
            item {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        } else if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = if (filter == AppFilter.LOCKED) Icons.Rounded.LockOpen else Icons.Rounded.Search,
                    title = if (filter == AppFilter.LOCKED) "No apps locked yet" else "No apps found",
                    message = if (filter == AppFilter.LOCKED) "Tap any app in All to lock it." else "Try a different search.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(visible, key = { it.packageName }) { app ->
            AppRow(app = app, locked = app.packageName in settings.lockedApps, onToggle = { toggle(app.packageName) })
        }
    }
}

@Composable
private fun ProtectionCard(settings: AppSettings, ready: Boolean, onToggle: (Boolean) -> Unit, onFix: () -> Unit) {
    val active = settings.protectionEnabled && ready
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Transparent,
    ) {
        Box(
            Modifier
                .background(if (settings.protectionEnabled) Brand.hero else Brand.paused)
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (active) Icons.Rounded.Shield else Icons.Rounded.GppMaybe,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !settings.protectionEnabled -> "Protection paused"
                                !ready -> "Almost ready"
                                else -> "You're protected"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "${settings.lockedApps.size} ${if (settings.lockedApps.size == 1) "app" else "apps"} locked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    Switch(
                        checked = settings.protectionEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Brand.Violet,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.25f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                if (settings.protectionEnabled && !ready) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onFix,
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Grant permissions to start locking apps  →",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, locked: Boolean, onToggle: () -> Unit) {
    val tint by animateColorAsState(
        if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        label = "lockTint",
    )
    val container by animateColorAsState(
        if (locked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        label = "lockBg",
    )
    val bounce by animateFloatAsState(if (locked) 1f else 0.9f, label = "bounce")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 46.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    app.packageName in LockEngine.UNINSTALL_GUARD && app.isSuggested -> "Recommended · protects settings"
                    app.isSuggested -> "Recommended"
                    else -> app.packageName
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .scale(bounce)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (locked) "Locked" else "Unlocked",
                tint = tint,
            )
        }
    }
}
