package com.vaultlock.app.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import com.vaultlock.app.security.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class VaultKind { PHOTO, VIDEO, FILE, NOTE }

data class VaultItem(
    val id: String,
    val kind: VaultKind,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long,
    val updatedAt: Long,
    val hasThumb: Boolean,
)

data class ImportResult(val imported: Int, val failed: Int, val originalsRemoved: Int)

class VaultRepository(private val context: Context, private val crypto: VaultCrypto) {

    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.bin")
    private val mutex = Mutex()
    private val thumbCache = LruCache<String, Bitmap>(120)
    private var loaded = false

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                _items.value = readIndex()
                loaded = true
            }
        }
    }

    suspend fun import(uris: List<Uri>, deleteOriginals: Boolean): ImportResult = withContext(Dispatchers.IO) {
        load()
        var imported = 0
        var failed = 0
        var removed = 0
        for (uri in uris) {
            val item = runCatching { importOne(uri) }.getOrNull()
            if (item == null) {
                failed++
                continue
            }
            imported++
            mutex.withLock {
                _items.value = listOf(item) + _items.value
                writeIndex(_items.value)
            }
            if (deleteOriginals) {
                val deleted = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
                if (deleted) removed++
            }
        }
        ImportResult(imported, failed, removed)
    }

    private fun importOne(uri: Uri): VaultItem {
        val resolver = context.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val kind = when {
            mime.startsWith("image/") -> VaultKind.PHOTO
            mime.startsWith("video/") -> VaultKind.VIDEO
            else -> VaultKind.FILE
        }
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.bin")
        val input = resolver.openInputStream(uri) ?: error("Unable to open $uri")
        input.use { crypto.encryptToFile(it, target) }
        if (size < 0) size = target.length()
        val thumb = runCatching {
            when (kind) {
                VaultKind.PHOTO -> photoThumb(uri)
                VaultKind.VIDEO -> videoThumb(uri)
                else -> null
            }
        }.getOrNull()
        if (thumb != null) {
            val bytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            File(dir, "$id.thumb").writeBytes(crypto.encryptBytes(bytes))
            thumbCache.put(id, thumb)
        }
        val now = System.currentTimeMillis()
        return VaultItem(id, kind, name, mime, size, now, now, thumb != null)
    }

    private fun photoThumb(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_SIZE) }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0
        return centerCrop(rotate(bitmap, rotation), THUMB_SIZE)
    }

    private fun videoThumb(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { centerCrop(it, THUMB_SIZE) }
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun thumbnail(item: VaultItem): Bitmap? = withContext(Dispatchers.IO) {
        thumbCache.get(item.id)?.let { return@withContext it }
        if (!item.hasThumb) return@withContext null
        runCatching {
            val bytes = crypto.decryptFile(File(dir, "${item.id}.thumb"))
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { thumbCache.put(item.id, it) }
    }

    suspend fun loadImage(item: VaultItem, maxSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeImage(crypto.decryptFile(File(dir, "${item.id}.bin")), maxSize) }.getOrNull()
    }

    suspend fun decryptToShared(item: VaultItem): File = withContext(Dispatchers.IO) {
        val shared = File(context.cacheDir, "shared/${item.id}").apply { mkdirs() }
        val target = File(shared, safeName(item.name))
        if (!target.exists()) {
            val tmp = File(shared, target.name + ".part")
            File(dir, "${item.id}.bin").inputStream().use { input ->
                tmp.outputStream().use { crypto.decrypt(input, it) }
            }
            tmp.renameTo(target)
        }
        target
    }

    fun clearShared(item: VaultItem) {
        File(context.cacheDir, "shared/${item.id}").deleteRecursively()
    }

    suspend fun export(items: List<VaultItem>, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        var count = 0
        for (item in items) {
            val ok = runCatching {
                val name = if (item.kind == VaultKind.NOTE) safeName(item.name.ifBlank { "Note" }) + ".txt" else item.name
                val mime = if (item.kind == VaultKind.NOTE) "text/plain" else item.mime
                val doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return@runCatching false
                resolver.openOutputStream(doc)?.use { out ->
                    File(dir, "${item.id}.bin").inputStream().use { crypto.decrypt(it, out) }
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
            if (ok) count++
        }
        count
    }

    suspend fun delete(ids: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ids.forEach { id ->
                File(dir, "$id.bin").delete()
                File(dir, "$id.thumb").delete()
                thumbCache.remove(id)
                File(context.cacheDir, "shared/$id").deleteRecursively()
            }
            _items.value = _items.value.filterNot { it.id in ids }
            writeIndex(_items.value)
        }
    }

    suspend fun readNote(item: VaultItem): String = withContext(Dispatchers.IO) {
        runCatching { String(crypto.decryptFile(File(dir, "${item.id}.bin")), Charsets.UTF_8) }.getOrDefault("")
    }

    suspend fun saveNote(id: String?, title: String, body: String): VaultItem = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val now = System.currentTimeMillis()
            val existing = id?.let { key -> _items.value.firstOrNull { it.id == key } }
            val itemId = existing?.id ?: UUID.randomUUID().toString()
            val bytes = body.toByteArray(Charsets.UTF_8)
            crypto.encryptToFile(ByteArrayInputStream(bytes), File(dir, "$itemId.bin"))
            val item = VaultItem(
                id = itemId,
                kind = VaultKind.NOTE,
                name = title,
                mime = "text/plain",
                size = bytes.size.toLong(),
                addedAt = existing?.addedAt ?: now,
                updatedAt = now,
                hasThumb = false,
            )
            _items.value = listOf(item) + _items.value.filterNot { it.id == itemId }
            writeIndex(_items.value)
            item
        }
    }

    private fun readIndex(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val json = JSONArray(String(crypto.decryptFile(indexFile), Charsets.UTF_8))
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                VaultItem(
                    id = o.getString("id"),
                    kind = VaultKind.valueOf(o.getString("kind")),
                    name = o.optString("name"),
                    mime = o.optString("mime"),
                    size = o.optLong("size"),
                    addedAt = o.optLong("added"),
                    updatedAt = o.optLong("updated", o.optLong("added")),
                    hasThumb = o.optBoolean("thumb"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(items: List<VaultItem>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kind", item.kind.name)
                    .put("name", item.name)
                    .put("mime", item.mime)
                    .put("size", item.size)
                    .put("added", item.addedAt)
                    .put("updated", item.updatedAt)
                    .put("thumb", item.hasThumb),
            )
        }
        crypto.encryptToFile(ByteArrayInputStream(json.toString().toByteArray(Charsets.UTF_8)), indexFile)
    }

    private fun safeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }

    companion object {
        private const val THUMB_SIZE = 320

        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
            return sample
        }

        fun rotationOf(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }

        fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        fun centerCrop(bitmap: Bitmap, size: Int): Bitmap {
            val side = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - side) / 2
            val y = (bitmap.height - side) / 2
            val square = Bitmap.createBitmap(bitmap, x, y, side, side)
            return if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square
        }

        fun decodeImage(bytes: ByteArray, maxSize: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize) }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val rotation = runCatching { rotationOf(ExifInterface(ByteArrayInputStream(bytes))) }.getOrDefault(0)
            return rotate(bitmap, rotation)
        }
    }
}
