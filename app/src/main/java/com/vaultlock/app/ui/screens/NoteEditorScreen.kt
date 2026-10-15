package com.vaultlock.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vaultlock.app.app
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(id: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = context.app.vault
    var noteId by remember { mutableStateOf(id) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("" to "") }
    var loaded by remember { mutableStateOf(id == null) }

    LaunchedEffect(id) {
        if (id != null) {
            vault.load()
            val item = vault.items.value.firstOrNull { it.id == id }
            if (item != null) {
                title = item.name
                body = vault.readNote(item)
                original = title to body
            }
            loaded = true
        }
    }

    fun saveAndExit() {
        val changed = (title to body) != original
        if (!loaded || !changed || (title.isBlank() && body.isBlank() && noteId == null)) {
            onBack()
            return
        }
        scope.launch {
            noteId = vault.saveNote(noteId, title.trim(), body).id
            onBack()
        }
    }

    BackHandler { saveAndExit() }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { saveAndExit() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (noteId != null) {
                        IconButton(onClick = {
                            val target = noteId ?: return@IconButton
                            scope.launch {
                                vault.delete(setOf(target))
                                onBack()
                            }
                        }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete note") }
                    }
                    IconButton(onClick = { saveAndExit() }) { Icon(Icons.Rounded.Check, contentDescription = "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
            TextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Start writing — everything here is encrypted.") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
