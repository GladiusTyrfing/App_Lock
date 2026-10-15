package com.vaultlock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class LockType { PIN, PATTERN }

enum class RelockPolicy { IMMEDIATELY, SCREEN_OFF, AFTER_TIMEOUT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Disguise { DEFAULT, CALCULATOR, WEATHER, CLOCK, NOTES }

data class AppSettings(
    val setupComplete: Boolean = false,
    val lockType: LockType = LockType.PIN,
    val credentialHash: String = "",
    val credentialSalt: String = "",
    val pinLength: Int = 4,
    val recoveryHash: String = "",
    val recoverySalt: String = "",
    val biometricEnabled: Boolean = true,
    val protectionEnabled: Boolean = true,
    val lockedApps: Set<String> = emptySet(),
    val relockPolicy: RelockPolicy = RelockPolicy.IMMEDIATELY,
    val relockMinutes: Int = 5,
    val intruderEnabled: Boolean = false,
    val intruderAttempts: Int = 2,
    val fakeCrash: Boolean = false,
    val randomKeypad: Boolean = false,
    val invisiblePattern: Boolean = false,
    val vibration: Boolean = true,
    val promptNewApps: Boolean = true,
    val uninstallProtection: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val disguise: Disguise = Disguise.DEFAULT,
    val blockScreenshots: Boolean = true,
    val deleteOriginals: Boolean = false,
    val intrudersSeenAt: Long = 0L,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context, private val scope: CoroutineScope) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val setupComplete = booleanPreferencesKey("setup_complete")
        val lockType = stringPreferencesKey("lock_type")
        val credentialHash = stringPreferencesKey("credential_hash")
        val credentialSalt = stringPreferencesKey("credential_salt")
        val pinLength = intPreferencesKey("pin_length")
        val recoveryHash = stringPreferencesKey("recovery_hash")
        val recoverySalt = stringPreferencesKey("recovery_salt")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        val protectionEnabled = booleanPreferencesKey("protection_enabled")
        val lockedApps = stringSetPreferencesKey("locked_apps")
        val relockPolicy = stringPreferencesKey("relock_policy")
        val relockMinutes = intPreferencesKey("relock_minutes")
        val intruderEnabled = booleanPreferencesKey("intruder_enabled")
        val intruderAttempts = intPreferencesKey("intruder_attempts")
        val fakeCrash = booleanPreferencesKey("fake_crash")
        val randomKeypad = booleanPreferencesKey("random_keypad")
        val invisiblePattern = booleanPreferencesKey("invisible_pattern")
        val vibration = booleanPreferencesKey("vibration")
        val promptNewApps = booleanPreferencesKey("prompt_new_apps")
        val uninstallProtection = booleanPreferencesKey("uninstall_protection")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val amoled = booleanPreferencesKey("amoled")
        val disguise = stringPreferencesKey("disguise")
        val blockScreenshots = booleanPreferencesKey("block_screenshots")
        val deleteOriginals = booleanPreferencesKey("delete_originals")
        val intrudersSeenAt = longPreferencesKey("intruders_seen_at")
    }

    private val flow: Flow<AppSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    val state: StateFlow<AppSettings> = flow.stateIn(
        scope,
        SharingStarted.Eagerly,
        runBlocking { flow.first() },
    )

    val current: AppSettings
        get() = state.value

    suspend fun edit(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            transform(prefs.toSettings()).writeTo(prefs)
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { edit(transform) }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            setupComplete = this[Keys.setupComplete] ?: d.setupComplete,
            lockType = this[Keys.lockType].toEnum(d.lockType),
            credentialHash = this[Keys.credentialHash] ?: d.credentialHash,
            credentialSalt = this[Keys.credentialSalt] ?: d.credentialSalt,
            pinLength = this[Keys.pinLength] ?: d.pinLength,
            recoveryHash = this[Keys.recoveryHash] ?: d.recoveryHash,
            recoverySalt = this[Keys.recoverySalt] ?: d.recoverySalt,
            biometricEnabled = this[Keys.biometricEnabled] ?: d.biometricEnabled,
            protectionEnabled = this[Keys.protectionEnabled] ?: d.protectionEnabled,
            lockedApps = this[Keys.lockedApps] ?: d.lockedApps,
            relockPolicy = this[Keys.relockPolicy].toEnum(d.relockPolicy),
            relockMinutes = this[Keys.relockMinutes] ?: d.relockMinutes,
            intruderEnabled = this[Keys.intruderEnabled] ?: d.intruderEnabled,
            intruderAttempts = this[Keys.intruderAttempts] ?: d.intruderAttempts,
            fakeCrash = this[Keys.fakeCrash] ?: d.fakeCrash,
            randomKeypad = this[Keys.randomKeypad] ?: d.randomKeypad,
            invisiblePattern = this[Keys.invisiblePattern] ?: d.invisiblePattern,
            vibration = this[Keys.vibration] ?: d.vibration,
            promptNewApps = this[Keys.promptNewApps] ?: d.promptNewApps,
            uninstallProtection = this[Keys.uninstallProtection] ?: d.uninstallProtection,
            themeMode = this[Keys.themeMode].toEnum(d.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            amoled = this[Keys.amoled] ?: d.amoled,
            disguise = this[Keys.disguise].toEnum(d.disguise),
            blockScreenshots = this[Keys.blockScreenshots] ?: d.blockScreenshots,
            deleteOriginals = this[Keys.deleteOriginals] ?: d.deleteOriginals,
            intrudersSeenAt = this[Keys.intrudersSeenAt] ?: d.intrudersSeenAt,
        )
    }

    private fun AppSettings.writeTo(prefs: MutablePreferences) {
        prefs[Keys.setupComplete] = setupComplete
        prefs[Keys.lockType] = lockType.name
        prefs[Keys.credentialHash] = credentialHash
        prefs[Keys.credentialSalt] = credentialSalt
        prefs[Keys.pinLength] = pinLength
        prefs[Keys.recoveryHash] = recoveryHash
        prefs[Keys.recoverySalt] = recoverySalt
        prefs[Keys.biometricEnabled] = biometricEnabled
        prefs[Keys.protectionEnabled] = protectionEnabled
        prefs[Keys.lockedApps] = lockedApps
        prefs[Keys.relockPolicy] = relockPolicy.name
        prefs[Keys.relockMinutes] = relockMinutes
        prefs[Keys.intruderEnabled] = intruderEnabled
        prefs[Keys.intruderAttempts] = intruderAttempts
        prefs[Keys.fakeCrash] = fakeCrash
        prefs[Keys.randomKeypad] = randomKeypad
        prefs[Keys.invisiblePattern] = invisiblePattern
        prefs[Keys.vibration] = vibration
        prefs[Keys.promptNewApps] = promptNewApps
        prefs[Keys.uninstallProtection] = uninstallProtection
        prefs[Keys.themeMode] = themeMode.name
        prefs[Keys.dynamicColor] = dynamicColor
        prefs[Keys.amoled] = amoled
        prefs[Keys.disguise] = disguise.name
        prefs[Keys.blockScreenshots] = blockScreenshots
        prefs[Keys.deleteOriginals] = deleteOriginals
        prefs[Keys.intrudersSeenAt] = intrudersSeenAt
    }
}
