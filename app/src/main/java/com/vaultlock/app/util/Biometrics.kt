package com.vaultlock.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrics {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun isHardwarePresent(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    fun hasFace(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)

    fun hasFingerprint(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    fun describe(context: Context): String = when {
        hasFingerprint(context) && hasFace(context) -> "Fingerprint or face"
        hasFace(context) -> "Face unlock"
        else -> "Fingerprint"
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onFallback: () -> Unit = {},
    ): BiometricPrompt? {
        if (!isAvailable(activity)) return null
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallback()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText("Use PIN or pattern")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        return runCatching { prompt.authenticate(info) }.map { prompt }.getOrNull()
    }
}
