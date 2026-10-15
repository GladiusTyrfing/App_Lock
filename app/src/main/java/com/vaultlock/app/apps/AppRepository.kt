package com.vaultlock.app.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isSuggested: Boolean,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, Bitmap>(220)
    private val labelCache = HashMap<String, String>()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val result = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val label = info.loadLabel(pm).toString()
                synchronized(labelCache) { labelCache[info.packageName] = label }
                InstalledApp(
                    packageName = info.packageName,
                    label = label,
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    isSuggested = info.packageName in SUGGESTED,
                )
            }
            .sortedBy { it.label.lowercase() }
        _apps.value = result
        _loading.value = false
    }

    fun label(packageName: String): String {
        synchronized(labelCache) { labelCache[packageName]?.let { return it } }
        val label = runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        }.getOrDefault(packageName)
        synchronized(labelCache) { labelCache[packageName] = label }
        return label
    }

    fun icon(packageName: String, sizePx: Int = 144): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        }.getOrNull()?.also { iconCache.put(packageName, it) }
    }

    fun isLaunchable(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    companion object {
        val SUGGESTED = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.discord",
            "com.google.android.gm",
            "com.google.android.apps.photos",
            "com.google.android.apps.messaging",
            "com.google.android.contacts",
            "com.google.android.dialer",
            "com.google.android.apps.docs",
            "com.google.android.apps.nbu.files",
            "com.android.chrome",
            "com.android.vending",
            "com.android.settings",
            "com.android.gallery3d",
            "com.sec.android.gallery3d",
            "com.miui.gallery",
            "com.samsung.android.messaging",
            "com.android.contacts",
            "com.paypal.android.p2pmobile",
        )
    }
}
