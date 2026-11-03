package com.tmplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
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
