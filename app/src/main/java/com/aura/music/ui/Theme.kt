package com.aura.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.aura.music.data.AppSettings

class Aura(
    val dark: Boolean, val accent: Color, val bg: List<Color>,
    val text: Color, val textDim: Color,
    val glassTop: Color, val glassBottom: Color, val rim: Color, val surface: Color
)

val LocalAura = staticCompositionLocalOf<Aura> { error("AuraTheme missing") }

val Accents = listOf(
    "Coral" to Color(0xFFF2503B),
    "Electric blue" to Color(0xFF4169E1),
    "Bubblegum" to Color(0xFFED9CD0),
    "Soft purple" to Color(0xFF817BE5),
    "Aqua" to Color(0xFF2EC4B6)
)

class GradientPreset(val name: String, val light: List<Color>, val dark: List<Color>)

val Gradients = listOf(
    GradientPreset("Lavender dream",
        listOf(Color(0xFFB9B3F0), Color(0xFF9F99EB), Color(0xFF817BE5)),
        listOf(Color(0xFF2A2F7A), Color(0xFF1A2260), Color(0xFF0E1440))),
    GradientPreset("Bubblegum",
        listOf(Color(0xFFF6C3E3), Color(0xFFED9CD0), Color(0xFFA8A1E8)),
        listOf(Color(0xFF4A1F5E), Color(0xFF2D1B5E), Color(0xFF14123A))),
    GradientPreset("Electric sky",
        listOf(Color(0xFFC3D0F8), Color(0xFF8FA6F2), Color(0xFFA8A1E8)),
        listOf(Color(0xFF1B3A9A), Color(0xFF14246E), Color(0xFF0A1040))),
    GradientPreset("Midnight",
        listOf(Color(0xFFD3D6F4), Color(0xFFB4B2EE), Color(0xFF959DE6)),
        listOf(Color(0xFF192B72), Color(0xFF101A4A), Color(0xFF080C2A)))
)

private val DeepBlue = Color(0xFF192B72)

fun buildAura(dark: Boolean, accent: Int, gradient: Int): Aura {
    val g = Gradients[gradient.coerceIn(0, Gradients.lastIndex)]
    return Aura(
        dark = dark,
        accent = Accents[accent.coerceIn(0, Accents.lastIndex)].second,
        bg = if (dark) g.dark else g.light,
        text = if (dark) Color.White else DeepBlue,
        textDim = if (dark) Color.White.copy(alpha = 0.68f) else DeepBlue.copy(alpha = 0.68f),
        glassTop = Color.White.copy(alpha = if (dark) 0.16f else 0.46f),
        glassBottom = Color.White.copy(alpha = if (dark) 0.05f else 0.20f),
        rim = Color.White.copy(alpha = if (dark) 0.42f else 0.85f),
        surface = if (dark) Color(0xFF1D2358) else Color(0xFFF1EFFF)
    )
}

@Composable
fun AuraTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val a = remember(dark, settings.accent, settings.gradient) { buildAura(dark, settings.accent, settings.gradient) }
    val scheme = if (dark) darkColorScheme(primary = a.accent, onPrimary = Color.White, surface = a.surface, onSurface = a.text, background = a.surface)
    else lightColorScheme(primary = a.accent, onPrimary = Color.White, surface = a.surface, onSurface = a.text, background = a.surface)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalAura provides a, LocalContentColor provides a.text, content = content)
    }
}
