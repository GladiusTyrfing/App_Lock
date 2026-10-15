package com.vaultlock.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultlock.app.data.AppSettings
import com.vaultlock.app.data.ThemeMode

object Brand {
    val Violet = Color(0xFF6C5CE7)
    val VioletLight = Color(0xFFA29BFE)
    val Indigo = Color(0xFF4834D4)
    val Mint = Color(0xFF00B894)
    val Coral = Color(0xFFFF7675)
    val Amber = Color(0xFFFDCB6E)
    val Night = Color(0xFF0F0E17)
    val NightRaised = Color(0xFF1B1830)

    val hero = Brush.linearGradient(listOf(Color(0xFF7B6CF6), Color(0xFF4834D4)))
    val lockBackground = Brush.verticalGradient(listOf(Color(0xFF221D45), Color(0xFF110F22), Color(0xFF07060D)))
    val paused = Brush.linearGradient(listOf(Color(0xFF636E72), Color(0xFF2D3436)))
}

private val LightColors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1F0F6B),
    secondary = Brand.Mint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF5EA),
    onSecondaryContainer = Color(0xFF00382B),
    tertiary = Color(0xFFE84393),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E7),
    onTertiaryContainer = Color(0xFF3E0020),
    error = Color(0xFFD63031),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE6E3F0),
    onSurfaceVariant = Color(0xFF5E5C6B),
    outline = Color(0xFF8E8B9C),
    outlineVariant = Color(0xFFD8D5E3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0EEF7),
    surfaceContainerHigh = Color(0xFFEAE8F2),
    surfaceContainerHighest = Color(0xFFE4E2EC),
)

private val DarkColors = darkColorScheme(
    primary = Brand.VioletLight,
    onPrimary = Color(0xFF1E1442),
    primaryContainer = Color(0xFF3B2F8F),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFF55EFC4),
    onSecondary = Color(0xFF003828),
    secondaryContainer = Color(0xFF00513D),
    onSecondaryContainer = Color(0xFFCDF5EA),
    tertiary = Color(0xFFFD79A8),
    onTertiary = Color(0xFF5E1133),
    tertiaryContainer = Color(0xFF7D294D),
    onTertiaryContainer = Color(0xFFFFD9E7),
    error = Color(0xFFFF7675),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Brand.Night,
    onBackground = Color(0xFFE6E3F0),
    surface = Brand.Night,
    onSurface = Color(0xFFE6E3F0),
    surfaceVariant = Color(0xFF2A2740),
    onSurfaceVariant = Color(0xFFC6C2D6),
    outline = Color(0xFF8F8BA3),
    outlineVariant = Color(0xFF3A374F),
    surfaceContainerLowest = Color(0xFF0A0912),
    surfaceContainerLow = Color(0xFF15131F),
    surfaceContainer = Color(0xFF1A1826),
    surfaceContainerHigh = Color(0xFF221F31),
    surfaceContainerHighest = Color(0xFF2B283C),
)

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08080C),
    surfaceContainer = Color(0xFF0E0E14),
    surfaceContainerHigh = Color(0xFF15151D),
    surfaceContainerHighest = Color(0xFF1C1C26),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun VaultLockTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = if (dark && settings.amoled) base.amoled() else base
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

@Composable
fun LockScreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, shapes = AppShapes, content = content)
}

val MonoDigits = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium)
