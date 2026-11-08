package com.aura.music.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Liquid glass" without runtime blur (Modifier.blur only works on Android 12+ and is costly):
 * a translucent vertical gradient, a bright rim and animated colour blobs behind everything
 * give the frosted look on every supported device.
 */
fun Modifier.glass(shape: Shape): Modifier = composed {
    val a = LocalAura.current
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(a.glassTop, a.glassBottom)))
        .border(1.dp, Brush.linearGradient(listOf(a.rim, a.rim.copy(alpha = 0.10f), a.rim.copy(alpha = 0.5f))), shape)
}

@Composable
fun AuraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val a = LocalAura.current
    val t = rememberInfiniteTransition(label = "bg")
    val p by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(45000, easing = LinearEasing)), label = "p")
    Box(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(a.bg))
            .drawBehind {
                val w = size.width; val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) =
                    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                blob(Color(0xFFED9CD0).copy(alpha = if (a.dark) 0.35f else 0.60f), w * (0.15f + 0.18f * sin(p)), h * (0.28f + 0.08f * cos(p)), w * 0.85f)
                blob(Color(0xFF4169E1).copy(alpha = if (a.dark) 0.50f else 0.38f), w * (0.85f + 0.12f * cos(p)), h * (0.62f + 0.10f * sin(p)), w * 0.95f)
                blob(Color.White.copy(alpha = if (a.dark) 0.07f else 0.45f), w * (0.55f + 0.2f * sin(p + 2f)), h * (0.10f + 0.05f * cos(p)), w * 0.6f)
            },
        content = content
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector, desc: String?, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: Dp = 46.dp, tint: Color? = null,
    selected: Boolean = false, accentFill: Boolean = false
) {
    val a = LocalAura.current
    val filled = accentFill || selected
    val shapeMod = if (filled) Modifier.clip(CircleShape)
        .background(Brush.verticalGradient(listOf(a.accent, a.accent.copy(alpha = 0.82f))))
        .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.15f))), CircleShape)
    else Modifier.glass(CircleShape)
    Box(
        modifier.size(size).then(shapeMod).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint ?: if (filled) Color.White else a.text, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun SectionHeader(title: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 10.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
