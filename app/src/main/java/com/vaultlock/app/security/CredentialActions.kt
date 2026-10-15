package com.vaultlock.app.security

import android.content.Context
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.data.LockType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CredentialActions {

    suspend fun saveCredential(context: Context, type: LockType, pinLength: Int, secret: String): AppSettings {
        val salt = Credentials.newSalt()
        val hash = withContext(Dispatchers.Default) { Credentials.hash(secret, salt) }
        return context.app.settings.edit {
            it.copy(
                lockType = type,
                pinLength = if (type == LockType.PIN) pinLength else it.pinLength,
                credentialSalt = salt,
                credentialHash = hash,
            )
        }
    }

    suspend fun newRecoveryCode(context: Context): String {
        val code = Credentials.newRecoveryCode()
        val salt = Credentials.newSalt()
        val hash = withContext(Dispatchers.Default) { Credentials.hash(Credentials.normalizeRecovery(code), salt) }
        context.app.settings.edit { it.copy(recoverySalt = salt, recoveryHash = hash) }
        return code
    }

    suspend fun verifyRecovery(settings: AppSettings, input: String): Boolean = withContext(Dispatchers.Default) {
        Credentials.verify(Credentials.normalizeRecovery(input), settings.recoverySalt, settings.recoveryHash)
    }
}
