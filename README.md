# VaultLock

A clean, modern app locker for Android 9.0 (API 28) and newer, built with Kotlin and Jetpack Compose (Material 3).

## Features

**Locking**
- Lock any installed app with a 4/6-digit PIN or a 3×3 pattern
- Fingerprint and face unlock through the system BiometricPrompt
- Instant detection through an accessibility service, with usage-access polling as a fallback
- Re-lock immediately, when the screen turns off, or after 1/5/15/30 minutes
- Escalating lockout after 5 wrong attempts
- Uninstall protection: optionally locks system Settings and the package installer
- Prompts you to lock newly installed apps from a notification
- Quick Settings tile to resume protection
- Starts again automatically after a reboot

**Vault**
- Hide photos, videos, files and notes
- Every item is encrypted with AES-256-GCM, using a data key wrapped by the Android Keystore
- Built-in photo viewer with pinch to zoom, and a video player (Media3)
- Export items back to any folder, multi-select, and optionally remove originals after import

**Stealth**
- Intruder selfie: the front camera captures a photo after N wrong attempts (stored encrypted)
- Fake "app keeps stopping" crash screen; long-press "Close app" to reveal the lock
- Disguise the launcher icon as Calculator, Weather, Clock or Notes
- Scrambled keypad and invisible pattern
- Screenshot and recent-apps blocking (FLAG_SECURE)

**Quality of life**
- Recovery code in case you forget your PIN or pattern
- Light, dark, dynamic color (Android 12+) and pure-black AMOLED themes
- Haptic feedback, search and filters (All / Locked / Suggested / System)
- No internet permission, no accounts, no tracking

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Every push also builds debug and release APKs in GitHub Actions; download them from the run's **Artifacts** section.

## First run

1. Choose a PIN or pattern and save the recovery code.
2. Grant **Display over other apps** and either **Accessibility** (recommended) or **Usage access**.
   On Android 13+ with a sideloaded APK, first open *App info → ⋮ → Allow restricted settings*.
3. Tap apps on the Apps tab to lock them.
