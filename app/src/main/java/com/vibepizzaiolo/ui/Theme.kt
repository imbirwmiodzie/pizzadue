package com.vibepizzaiolo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** "Oven at night": dark, so the screen can stay at full brightness without glaring. */
object Pal {
    val bg = Color(0xFF1A1E23)
    val card = Color(0xFF242A31)
    val raised = Color(0xFF353D47)
    val line = Color(0xFF333A43)
    val fg = Color(0xFFF3EFE6)
    val muted = Color(0xFF9AA3AD)
    val flame = Color(0xFFFFB020)
    val ember = Color(0xFFFF8A3D)
    val basil = Color(0xFF5FCF8A)
    val tomato = Color(0xFFFF6B57)
    val ink = Color(0xFF1A1E23)
}

val Mono = FontFamily.Monospace

@Composable
fun PizzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.flame, onPrimary = Pal.ink,
            secondary = Pal.basil, onSecondary = Pal.ink,
            background = Pal.bg, onBackground = Pal.fg,
            surface = Pal.bg, onSurface = Pal.fg,
            surfaceVariant = Pal.card, onSurfaceVariant = Pal.muted,
            surfaceContainer = Pal.card,
            outline = Pal.line, error = Pal.tomato,
        ),
        typography = Typography(
            headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyMedium = TextStyle(fontSize = 14.sp),
            labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
