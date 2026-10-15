package com.vaultlock.app

import android.app.Application
import android.content.Context
import com.vaultlock.app.apps.AppRepository
import com.vaultlock.app.data.SettingsStore
import com.vaultlock.app.lock.IntruderRepository
import com.vaultlock.app.lock.Notifications
import com.vaultlock.app.security.VaultCrypto
import com.vaultlock.app.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class VaultLockApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
        private set

    val crypto by lazy { VaultCrypto(File(filesDir, "keys")) }
    val apps by lazy { AppRepository(this) }
    val vault by lazy { VaultRepository(this, crypto) }
    val intruders by lazy { IntruderRepository(this, crypto) }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this, scope)
        Notifications.createChannels(this)
        File(cacheDir, "shared").deleteRecursively()
    }
}

val Context.app: VaultLockApp
    get() = applicationContext as VaultLockApp
