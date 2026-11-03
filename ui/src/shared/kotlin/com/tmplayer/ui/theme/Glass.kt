package com.tmplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val LocalGlassTheme = staticCompositionLocalOf { false }

/** A legible tinted glass layer: highlights stay at the edges, never over the text. */
fun Modifier.glassPanel(): Modifier = composed {
    if (!LocalGlassTheme.current) return@composed this
    this.drawWithCache {
        val radius = CornerRadius(24.dp.toPx())
        val wash = Brush.linearGradient(listOf(Color(0x384FF2E5), Color(0x183F4F81), Color(0x247F6CFF)),
            Offset.Zero, Offset(size.width, size.height))
        val shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.08f)))
        onDrawBehind {
            drawRoundRect(wash, cornerRadius = radius)
            drawRoundRect(shine, cornerRadius = radius, style = Stroke(1.dp.toPx()))
        }
    }
}

/** The same backdrop under all Android screens, without per-frame blur work. */
@androidx.compose.runtime.Composable
fun GlassBackdrop(content: @androidx.compose.runtime.Composable () -> Unit) {
    if (!LocalGlassTheme.current) { content(); return }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().drawWithCache {
        val ground = Brush.linearGradient(listOf(Color(0xFF071523), Color(0xFF26395A), Color(0xFF102638)),
            Offset.Zero, Offset(size.width, size.height))
        val bloom = Brush.radialGradient(listOf(Color(0x5552D9DC), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.25f), radius = size.maxDimension * 0.7f)
        onDrawBehind { drawRect(ground); drawRect(bloom) }
    }) { content() }
}
