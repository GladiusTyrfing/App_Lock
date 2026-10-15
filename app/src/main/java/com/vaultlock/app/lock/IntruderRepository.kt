package com.vaultlock.app.lock

import android.content.Context
import android.graphics.Bitmap
import com.vaultlock.app.security.VaultCrypto
import com.vaultlock.app.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class IntruderRecord(
    val fileName: String,
    val packageName: String,
    val timestamp: Long,
)

class IntruderRepository(context: Context, private val crypto: VaultCrypto) {

    private val dir = File(context.filesDir, "intruders").apply { mkdirs() }

    private val _records = MutableStateFlow(scan())
    val records: StateFlow<List<IntruderRecord>> = _records

    fun store(photo: File, packageName: String) {
        runCatching {
            val time = System.currentTimeMillis()
            val target = File(dir, "${time}_$packageName.bin")
            photo.inputStream().use { crypto.encryptToFile(it, target) }
        }
        photo.delete()
        _records.value = scan()
    }

    suspend fun load(record: IntruderRecord, maxSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { VaultRepository.decodeImage(crypto.decryptFile(File(dir, record.fileName)), maxSize) }.getOrNull()
    }

    fun delete(record: IntruderRecord) {
        File(dir, record.fileName).delete()
        _records.value = scan()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        _records.value = scan()
    }

    private fun scan(): List<IntruderRecord> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".bin") }
            .mapNotNull { file ->
                val base = file.name.removeSuffix(".bin")
                val time = base.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
                IntruderRecord(file.name, base.substringAfter('_'), time)
            }
            .sortedByDescending { it.timestamp }
}
