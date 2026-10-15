package com.vaultlock.app.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Credentials {

    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(secret: String, salt: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun verify(secret: String, salt: String, expected: String): Boolean {
        if (salt.isEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), expected.toByteArray())
    }

    fun newRecoveryCode(): String =
        (0 until 12)
            .map { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
            .chunked(4)
            .joinToString("-") { it.joinToString("") }

    fun normalizeRecovery(input: String): String =
        input.uppercase().filter { it.isLetterOrDigit() }
}
