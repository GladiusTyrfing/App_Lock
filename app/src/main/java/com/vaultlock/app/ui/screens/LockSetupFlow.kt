package com.vaultlock.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vaultlock.app.data.LockType
import com.vaultlock.app.security.CredentialActions
import com.vaultlock.app.ui.components.CredentialSetup
import com.vaultlock.app.ui.components.IconBubble
import kotlinx.coroutines.launch

@Composable
fun LockSetupFlow(onSaved: () -> Unit, modifier: Modifier = Modifier, header: String = "Choose how to unlock") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<LockType?>(null) }
    var pinLength by remember { mutableIntStateOf(4) }
    var saving by remember { mutableStateOf(false) }

    AnimatedContent(targetState = type, label = "setup", modifier = modifier) { selected ->
        if (selected == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(header, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "You'll use this to open locked apps, your vault and VaultLock itself. Biometrics can be added on top.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                LockOption(Icons.Rounded.Pin, "4-digit PIN", "Quick and familiar") {
                    pinLength = 4
                    type = LockType.PIN
                }
                Spacer(Modifier.height(12.dp))
                LockOption(Icons.Rounded.Dialpad, "6-digit PIN", "Stronger, a million combinations") {
                    pinLength = 6
                    type = LockType.PIN
                }
                Spacer(Modifier.height(12.dp))
                LockOption(Icons.Rounded.Gesture, "Pattern", "Draw a shape across a 3×3 grid") {
                    type = LockType.PATTERN
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { type = null }, enabled = !saving) { Text("Change type") }
                }
                Spacer(Modifier.weight(1f))
                CredentialSetup(
                    type = selected,
                    pinLength = pinLength,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onComplete = { secret ->
                        if (!saving) {
                            saving = true
                            scope.launch {
                                CredentialActions.saveCredential(context, selected, pinLength, secret)
                                onSaved()
                            }
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LockOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, MaterialTheme.colorScheme.primary, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
