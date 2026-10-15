package com.vaultlock.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import com.vaultlock.app.BuildConfig
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.data.Disguise
import com.vaultlock.app.data.LockType
import com.vaultlock.app.data.RelockPolicy
import com.vaultlock.app.data.ThemeMode
import com.vaultlock.app.ui.components.ChoiceDialog
import com.vaultlock.app.ui.components.SectionHeader
import com.vaultlock.app.ui.components.SettingRow
import com.vaultlock.app.ui.components.SettingsGroup
import com.vaultlock.app.ui.components.SwitchRow
import com.vaultlock.app.ui.theme.Brand
import com.vaultlock.app.util.AppSession
import com.vaultlock.app.util.Biometrics
import com.vaultlock.app.util.Disguises
import com.vaultlock.app.util.Permissions

private enum class SettingsDialog { RELOCK, DISGUISE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onOpenPermissions: () -> Unit,
    onChangeLock: () -> Unit,
    onNewRecovery: () -> Unit,
) {
    val context = LocalContext.current
    val store = context.app.settings
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val biometricAvailable = Biometrics.isAvailable(context)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) store.update { it.copy(intruderEnabled = true) }
    }

    Scaffold(
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        topBar = { TopAppBar(title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SectionHeader("Security")
                SettingsGroup {
                    SettingRow(
                        icon = Icons.Rounded.Password,
                        title = "Change lock",
                        subtitle = when (settings.lockType) {
                            LockType.PIN -> "${settings.pinLength}-digit PIN"
                            LockType.PATTERN -> "Pattern"
                        },
                        onClick = onChangeLock,
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric unlock",
                        subtitle = if (biometricAvailable) Biometrics.describe(context) else "Enrol a fingerprint or face in system settings first",
                        checked = settings.biometricEnabled && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = { value -> store.update { it.copy(biometricEnabled = value) } },
                    )
                    if (settings.lockType == LockType.PIN) {
                        SwitchRow(
                            icon = Icons.Rounded.Shuffle,
                            title = "Scramble keypad",
                            subtitle = "Shuffle digits so nobody can learn your PIN by watching",
                            checked = settings.randomKeypad,
                            onCheckedChange = { value -> store.update { it.copy(randomKeypad = value) } },
                        )
                    } else {
                        SwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Invisible pattern",
                            subtitle = "Hide the trail while drawing",
                            checked = settings.invisiblePattern,
                            onCheckedChange = { value -> store.update { it.copy(invisiblePattern = value) } },
                        )
                    }
                    SwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = "Vibrate on touch",
                        subtitle = null,
                        checked = settings.vibration,
                        onCheckedChange = { value -> store.update { it.copy(vibration = value) } },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Key,
                        title = "New recovery code",
                        subtitle = "Replace the code used when you forget your lock",
                        onClick = onNewRecovery,
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
            item {
                SectionHeader("Locking")
                SettingsGroup {
                    SettingRow(
                        icon = Icons.Rounded.Timer,
                        title = "Re-lock",
                        subtitle = relockLabel(settings),
                        onClick = { dialog = SettingsDialog.RELOCK },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.NewReleases,
                        title = "Suggest new apps",
                        subtitle = "Ask to lock apps right after they're installed",
                        checked = settings.promptNewApps,
                        onCheckedChange = { value -> store.update { it.copy(promptNewApps = value) } },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = "Uninstall protection",
                        subtitle = "Lock system Settings and the package installer so VaultLock can't be removed or disabled",
                        checked = settings.uninstallProtection,
                        onCheckedChange = { value -> store.update { it.copy(uninstallProtection = value) } },
                    )
                    SettingRow(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "Permissions",
                        subtitle = if (Permissions.fullyReady(context)) "Everything is set up" else "Some permissions are missing",
                        tint = if (Permissions.fullyReady(context)) Brand.Mint else MaterialTheme.colorScheme.error,
                        onClick = onOpenPermissions,
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
            item {
                SectionHeader("Stealth")
                SettingsGroup {
                    SwitchRow(
                        icon = Icons.Rounded.CameraFront,
                        title = "Intruder selfie",
                        subtitle = "Photograph anyone who enters a wrong code ${settings.intruderAttempts}×",
                        checked = settings.intruderEnabled && Permissions.hasCamera(context),
                        onCheckedChange = { value ->
                            if (value && !Permissions.hasCamera(context)) {
                                AppSession.expectExternal()
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                store.update { it.copy(intruderEnabled = value) }
                            }
                        },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    SwitchRow(
                        icon = Icons.Rounded.TheaterComedy,
                        title = "Fake crash screen",
                        subtitle = "Locked apps look like they crashed. Long-press \"Close app\" to unlock",
                        checked = settings.fakeCrash,
                        onCheckedChange = { value -> store.update { it.copy(fakeCrash = value) } },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    SettingRow(
                        icon = Icons.Rounded.Apps,
                        title = "Disguise app icon",
                        subtitle = "Currently shown as ${Disguises.label(settings.disguise)}",
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = { dialog = SettingsDialog.DISGUISE },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Screenshot,
                        title = "Block screenshots",
                        subtitle = "Hide VaultLock from screenshots and the recent apps preview",
                        checked = settings.blockScreenshots,
                        onCheckedChange = { value -> store.update { it.copy(blockScreenshots = value) } },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    SwitchRow(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Remove originals",
                        subtitle = "Delete files from their original location after hiding them in the vault",
                        checked = settings.deleteOriginals,
                        onCheckedChange = { value -> store.update { it.copy(deleteOriginals = value) } },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item {
                SectionHeader("Appearance")
                SettingsGroup {
                    SettingRow(icon = Icons.Rounded.Brightness6, title = "Theme", subtitle = null)
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { store.update { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Auto"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    },
                                )
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        SwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = "Dynamic color",
                            subtitle = "Match your wallpaper colors",
                            checked = settings.dynamicColor,
                            onCheckedChange = { value -> store.update { it.copy(dynamicColor = value) } },
                        )
                    }
                    SwitchRow(
                        icon = Icons.Rounded.Contrast,
                        title = "Pure black",
                        subtitle = "AMOLED-friendly dark theme",
                        checked = settings.amoled,
                        onCheckedChange = { value -> store.update { it.copy(amoled = value) } },
                    )
                }
            }
            item {
                SectionHeader("About")
                SettingsGroup {
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = "VaultLock ${BuildConfig.VERSION_NAME}",
                        subtitle = "Everything stays on this device. No accounts, no tracking, no ads.",
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.RELOCK -> ChoiceDialog(
            title = "Re-lock apps",
            options = listOf(
                (RelockPolicy.IMMEDIATELY to 0) to "Immediately after leaving",
                (RelockPolicy.SCREEN_OFF to 0) to "When the screen turns off",
                (RelockPolicy.AFTER_TIMEOUT to 1) to "After 1 minute",
                (RelockPolicy.AFTER_TIMEOUT to 5) to "After 5 minutes",
                (RelockPolicy.AFTER_TIMEOUT to 15) to "After 15 minutes",
                (RelockPolicy.AFTER_TIMEOUT to 30) to "After 30 minutes",
            ),
            selected = settings.relockPolicy to if (settings.relockPolicy == RelockPolicy.AFTER_TIMEOUT) settings.relockMinutes else 0,
            onSelect = { (policy, minutes) ->
                store.update { it.copy(relockPolicy = policy, relockMinutes = if (minutes > 0) minutes else it.relockMinutes) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DISGUISE -> ChoiceDialog(
            title = "Disguise app icon",
            options = Disguise.entries.map { it to Disguises.label(it) },
            selected = settings.disguise,
            onSelect = { disguise ->
                store.update { it.copy(disguise = disguise) }
                Disguises.apply(context, disguise)
            },
            onDismiss = { dialog = null },
            leading = { disguise ->
                val bitmap = remember(disguise) {
                    ContextCompat.getDrawable(context, Disguises.icon(disguise))?.toBitmap(108, 108)?.asImageBitmap()
                }
                if (bitmap != null) Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            },
        )
        null -> Unit
    }
}

private fun relockLabel(settings: AppSettings): String = when (settings.relockPolicy) {
    RelockPolicy.IMMEDIATELY -> "Immediately after leaving the app"
    RelockPolicy.SCREEN_OFF -> "When the screen turns off"
    RelockPolicy.AFTER_TIMEOUT -> "${settings.relockMinutes} min after leaving, or when the screen turns off"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeLockScreen(onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change lock") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LockSetupFlow(onSaved = onDone, header = "Pick a new lock", modifier = Modifier.padding(padding))
    }
}

@Composable
fun NewRecoveryScreen(onDone: () -> Unit) {
    RecoveryStep(onContinue = onDone, buttonText = "Done")
}
