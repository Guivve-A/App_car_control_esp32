package com.example.bluex.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BluexColorScheme = darkColorScheme(
    primary = BluexAccent,
    onPrimary = BluexTextOnAccent,
    primaryContainer = BluexAccentDark,
    onPrimaryContainer = BluexTextPrimary,

    secondary = BluexPurple,
    onSecondary = BluexTextOnAccent,
    secondaryContainer = Color(0xFF3A2D5E),
    onSecondaryContainer = BluexTextPrimary,

    tertiary = BluexTeal,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1A3A4A),
    onTertiaryContainer = BluexTextPrimary,

    background = BluexDarkBg,
    onBackground = BluexTextPrimary,

    surface = BluexDarkSurface,
    onSurface = BluexTextPrimary,
    surfaceVariant = BluexDarkSurfaceElevated,
    onSurfaceVariant = BluexTextSecondary,

    outline = GlassBorder,
    outlineVariant = BluexSeparator,

    error = BluexRed,
    onError = BluexTextOnAccent,
)

@Composable
fun BluexTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BluexDarkBg.toArgb()
            window.navigationBarColor = BluexDarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = BluexColorScheme,
        typography = BluexTypography,
        content = content
    )
}
