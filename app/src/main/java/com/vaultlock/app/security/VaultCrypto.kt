package com.vaultlock.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultCrypto(private val dir: File) {

    private val random = SecureRandom()
    private val keyFile = File(dir, "data.key")

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Synchronized
    private fun dataKey(): SecretKey {
        cachedKey?.let { return it }
        val master = masterKey()
        val key = if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
            SecretKeySpec(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), "AES")
        } else {
            val raw = ByteArray(32).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, master)
            dir.mkdirs()
            val tmp = File(dir, "data.key.tmp")
            tmp.writeBytes(cipher.iv + cipher.doFinal(raw))
            tmp.renameTo(keyFile)
            SecretKeySpec(raw, "AES")
        }
        cachedKey = key
        return key
    }

    fun encrypt(input: InputStream, output: OutputStream) {
        val key = dataKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val out = DataOutputStream(BufferedOutputStream(output, CHUNK_SIZE))
        var current = readChunk(input)
        var index = 0L
        while (true) {
            val next = if (current.size == CHUNK_SIZE) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            val iv = ByteArray(IV_SIZE).also(random::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(index, last))
            val sealed = cipher.doFinal(current)
            out.writeByte(if (last) 1 else 0)
            out.write(iv)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            current = next
            index++
        }
        out.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream) {
        val key = dataKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val data = DataInputStream(BufferedInputStream(input, CHUNK_SIZE))
        var index = 0L
        while (true) {
            val flag = data.read()
            if (flag < 0) throw IOException("Encrypted data is truncated")
            val iv = ByteArray(IV_SIZE)
            data.readFully(iv)
            val length = data.readInt()
            if (length < TAG_BITS / 8 || length > CHUNK_SIZE + TAG_BITS / 8) throw IOException("Encrypted data is corrupt")
            val sealed = ByteArray(length)
            data.readFully(sealed)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(index, flag == 1))
            output.write(cipher.doFinal(sealed))
            if (flag == 1) break
            index++
        }
        output.flush()
    }

    fun encryptBytes(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 64)
        encrypt(ByteArrayInputStream(bytes), out)
        return out.toByteArray()
    }

    fun decryptBytes(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        decrypt(ByteArrayInputStream(bytes), out)
        return out.toByteArray()
    }

    fun encryptToFile(input: InputStream, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.outputStream().use { encrypt(input, it) }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Could not write ${target.name}")
        }
    }

    fun decryptFile(source: File): ByteArray = source.inputStream().use { input ->
        val out = ByteArrayOutputStream(source.length().toInt().coerceAtLeast(0))
        decrypt(input, out)
        out.toByteArray()
    }

    private fun readChunk(input: InputStream): ByteArray {
        val buffer = ByteArray(CHUNK_SIZE)
        var read = 0
        while (read < CHUNK_SIZE) {
            val count = input.read(buffer, read, CHUNK_SIZE - read)
            if (count < 0) break
            read += count
        }
        return if (read == CHUNK_SIZE) buffer else buffer.copyOf(read)
    }

    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(9).putLong(index).put(if (last) 1.toByte() else 0.toByte()).array()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vaultlock_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val CHUNK_SIZE = 64 * 1024
    }
}
