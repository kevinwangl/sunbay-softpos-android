package com.sunbay.softpos.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = SunmiOrange,
    secondary = SunmiOrangeLight,
    tertiary = SunmiOrangeDark,
    background = SunmiBlack,
    surface = SunmiDarkGray,
    onPrimary = SunmiWhite,
    onSecondary = SunmiBlack,
    onTertiary = SunmiWhite,
    onBackground = SunmiWhite,
    onSurface = SunmiWhite,
    error = ErrorRed,
    onError = SunmiWhite
)

private val LightColorScheme = lightColorScheme(
    primary = SunmiOrange,
    secondary = SunmiOrangeLight,
    tertiary = SunmiOrangeDark,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = SunmiWhite,
    onSecondary = SunmiBlack,
    onTertiary = SunmiWhite,
    onBackground = SunmiBlack,
    onSurface = SunmiBlack,
    error = ErrorRed,
    onError = SunmiWhite
)

@Composable
fun SunbaySoftPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
