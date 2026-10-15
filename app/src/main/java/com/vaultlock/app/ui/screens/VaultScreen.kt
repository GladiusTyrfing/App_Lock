package com.vaultlock.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.ui.components.EmptyState
import com.vaultlock.app.ui.components.IconBubble
import com.vaultlock.app.ui.components.formatSize
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.vault.VaultItem
import com.vaultlock.app.vault.VaultKind
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class VaultTab(val kind: VaultKind, val label: String, val icon: ImageVector, val mimes: Array<String>)

private val vaultTabs = listOf(
    VaultTab(VaultKind.PHOTO, "Photos", Icons.Rounded.PhotoLibrary, arrayOf("image/*")),
    VaultTab(VaultKind.VIDEO, "Videos", Icons.Rounded.VideoLibrary, arrayOf("video/*")),
    VaultTab(VaultKind.FILE, "Files", Icons.Rounded.Description, arrayOf("*/*")),
    VaultTab(VaultKind.NOTE, "Notes", Icons.Rounded.EditNote, emptyArray()),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(settings: AppSettings, onOpenItem: (String) -> Unit, onOpenNote: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = context.app.vault
    val items by vault.items.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val tab = vaultTabs[tabIndex]
    val tabItems = remember(items, tab) { items.filter { it.kind == tab.kind } }

    LaunchedEffect(Unit) { vault.load() }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = vault.import(uris, settings.deleteOriginals)
            busy = false
            val parts = buildList {
                add("${result.imported} hidden")
                if (result.failed > 0) add("${result.failed} failed")
                if (settings.deleteOriginals && result.originalsRemoved < result.imported) {
                    add("${result.imported - result.originalsRemoved} originals kept")
                }
            }
            snackbar.showSnackbar(parts.joinToString(" · "))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        val chosen = items.filter { it.id in selection }
        busy = true
        scope.launch {
            val count = vault.export(chosen, tree)
            busy = false
            selection = emptySet()
            snackbar.showSnackbar("Exported $count of ${chosen.size}")
        }
    }

    fun openFile(item: VaultItem) {
        scope.launch {
            busy = true
            val file = runCatching { vault.decryptToShared(item) }.getOrNull()
            busy = false
            if (file == null) {
                Toast.makeText(context, "Couldn't open this file", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            AppSession.expectExternal()
            runCatching { context.startActivity(Intent.createChooser(intent, item.name)) }
        }
    }

    fun onItemClick(item: VaultItem) {
        if (selection.isNotEmpty()) {
            selection = if (item.id in selection) selection - item.id else selection + item.id
            return
        }
        when (item.kind) {
            VaultKind.PHOTO, VaultKind.VIDEO -> onOpenItem(item.id)
            VaultKind.NOTE -> onOpenNote(item.id)
            VaultKind.FILE -> openFile(item)
        }
    }

    fun onItemLongClick(item: VaultItem) {
        selection = selection + item.id
    }

    Scaffold(
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Vault", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${items.size} encrypted ${if (items.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        IconButton(onClick = { selection = tabItems.map { it.id }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            AppSession.expectExternal()
                            exportLauncher.launch(null)
                        }) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = "Export")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selection.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (tab.kind == VaultKind.NOTE) {
                            onOpenNote(null)
                        } else {
                            AppSession.expectExternal()
                            importLauncher.launch(tab.mimes)
                        }
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(if (tab.kind == VaultKind.NOTE) "New note" else "Hide ${tab.label.lowercase()}") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tabIndex, containerColor = MaterialTheme.colorScheme.surface) {
                vaultTabs.forEachIndexed { index, item ->
                    val count = items.count { it.kind == item.kind }
                    Tab(
                        selected = tabIndex == index,
                        onClick = {
                            tabIndex = index
                            selection = emptySet()
                        },
                        text = { Text(if (count > 0) "${item.label} $count" else item.label, maxLines = 1) },
                    )
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tabItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = tab.icon,
                        title = when (tab.kind) {
                            VaultKind.PHOTO -> "No hidden photos"
                            VaultKind.VIDEO -> "No hidden videos"
                            VaultKind.FILE -> "No hidden files"
                            VaultKind.NOTE -> "No secret notes"
                        },
                        message = if (tab.kind == VaultKind.NOTE) {
                            "Write notes that are encrypted on your device."
                        } else {
                            "Everything you add is encrypted with AES-256 and only visible inside VaultLock."
                        },
                    )
                }
            } else {
                when (tab.kind) {
                    VaultKind.PHOTO, VaultKind.VIDEO -> MediaGrid(tabItems, selection, ::onItemClick, ::onItemLongClick)
                    VaultKind.FILE, VaultKind.NOTE -> ItemList(tabItems, selection, ::onItemClick, ::onItemLongClick)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selection.size} ${if (selection.size == 1) "item" else "items"}?") },
            text = { Text("They'll be permanently erased from the vault. Export them first if you want to keep a copy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = selection
                    selection = emptySet()
                    scope.launch { vault.delete(ids) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<VaultItem>,
    selection: Set<String>,
    onClick: (VaultItem) -> Unit,
    onLongClick: (VaultItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = item.id in selection
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) }),
            ) {
                Thumbnail(item, Modifier.fillMaxSize())
                if (item.kind == VaultKind.VIDEO) {
                    Icon(
                        Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    )
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun Thumbnail(item: VaultItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, item.id) { value = context.app.vault.thumbnail(item) }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemList(
    items: List<VaultItem>,
    selection: Set<String>,
    onClick: (VaultItem) -> Unit,
    onLongClick: (VaultItem) -> Unit,
) {
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = item.id in selection
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) }),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(
                        when {
                            item.kind == VaultKind.NOTE -> Icons.Rounded.EditNote
                            else -> Icons.Rounded.FileOpen
                        },
                        if (item.kind == VaultKind.NOTE) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name.ifBlank { "Untitled note" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (item.kind == VaultKind.NOTE) {
                                "Edited ${format.format(Date(item.updatedAt))}"
                            } else {
                                "${formatSize(item.size)} · ${format.format(Date(item.addedAt))}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
