package com.spotlin.messenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = SpotlinPrimaryLight,
    onPrimary = SpotlinOnPrimaryLight,
    primaryContainer = SpotlinPrimaryContainerLight,
    background = SpotlinBackgroundLight,
    surface = SpotlinSurfaceLight,
    surfaceVariant = SpotlinSurfaceVariantLight
)

private val DarkScheme = darkColorScheme(
    primary = SpotlinPrimaryDark,
    onPrimary = SpotlinOnPrimaryDark,
    primaryContainer = SpotlinPrimaryContainerDark,
    background = SpotlinBackgroundDark,
    surface = SpotlinSurfaceDark,
    surfaceVariant = SpotlinSurfaceVariantDark
)

private val PinkScheme = lightColorScheme(
    primary = SpotlinPinkPrimary,
    onPrimary = SpotlinPinkOnPrimary,
    primaryContainer = SpotlinPinkContainer,
    background = SpotlinPinkBackground,
    surface = SpotlinPinkSurface,
    surfaceVariant = SpotlinPinkSurfaceVariant
)

enum class ThemeMode {
    MATERIAL_YOU,
    LIGHT,
    DARK,
    PINK
}

@Composable
fun SpotlinTheme(
    themeMode: ThemeMode = ThemeMode.MATERIAL_YOU,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        ThemeMode.PINK -> PinkScheme
        ThemeMode.LIGHT -> LightScheme
        ThemeMode.DARK -> DarkScheme
        ThemeMode.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkScheme else LightScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val isLight = themeMode == ThemeMode.LIGHT || (themeMode == ThemeMode.MATERIAL_YOU && !systemDark) || themeMode == ThemeMode.PINK
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
