package com.textdoorman.android.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand teal, used when the phone doesn't offer wallpaper-based colors.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F6B), onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F1EA), onPrimaryContainer = Color(0xFF00201E),
    secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF051F1D),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8AD4CD), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C), onPrimaryContainer = Color(0xFFA6F1EA),
    secondaryContainer = Color(0xFF324B48), onSecondaryContainer = Color(0xFFCCE8E4),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ScreenerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
