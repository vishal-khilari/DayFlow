package com.dayflow.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Accents = listOf(
    Color(0xFF4F46E5), // indigo
    Color(0xFF0EA5E9), // sky
    Color(0xFF10B981), // emerald
    Color(0xFFF59E0B), // amber
    Color(0xFFEC4899), // pink
    Color(0xFF8B5CF6)  // violet
)
val AccentNames = listOf("Indigo", "Sky", "Emerald", "Amber", "Pink", "Violet")

val GreenC = Color(0xFF22C55E)
val RedC = Color(0xFFEF4444)
val OrangeC = Color(0xFFF97316)
val BlueC = Color(0xFF3B82F6)
val GreyC = Color(0xFF94A3B8)

fun priorityColor(p: Int): Color = when (p) {
    0 -> GreyC
    1 -> BlueC
    2 -> OrangeC
    else -> RedC
}

val PriorityNames = listOf("Low", "Medium", "High", "Urgent")

@Composable
fun DayFlowTheme(content: @Composable () -> Unit) {
    val dark = when (Store.prefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val accent = Accents[Store.prefs.accent.coerceIn(0, Accents.size - 1)]
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.30f),
            onPrimaryContainer = Color.White,
            secondaryContainer = accent.copy(alpha = 0.30f),
            onSecondaryContainer = Color.White,
            background = Color(0xFF0F1115),
            onBackground = Color(0xFFECEEF4),
            surface = Color(0xFF181B22),
            onSurface = Color(0xFFECEEF4),
            surfaceVariant = Color(0xFF262B37),
            onSurfaceVariant = Color(0xFF9AA3B5),
            outline = Color(0xFF3A4050)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = Color(0xFF12141A),
            secondaryContainer = accent.copy(alpha = 0.16f),
            onSecondaryContainer = Color(0xFF12141A),
            background = Color(0xFFF6F7FB),
            onBackground = Color(0xFF12141A),
            surface = Color.White,
            onSurface = Color(0xFF12141A),
            surfaceVariant = Color(0xFFECEEF6),
            onSurfaceVariant = Color(0xFF5B6478),
            outline = Color(0xFFD5D9E4)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            w.statusBarColor = scheme.background.toArgb()
            w.navigationBarColor = scheme.surface.toArgb()
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
