package com.tmplayer.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.tmplayer.data.MediaItem
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.rememberArtworkTint
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.online.rememberMeta
import com.tmplayer.ui.theme.Tone

internal val LocalHomeArtworkSelection = compositionLocalOf<(MediaItem) -> Unit> { {} }

@Composable
internal fun HomeArtworkGround(item: MediaItem?, chatTitle: (Long) -> String) {
    if (item == null) return
    val meta = rememberMeta(item, showOnly = true)
    val cover = rememberCover(item, chatTitle(item.chatId))
    val tint = rememberArtworkTint(BackdropSource(item.miniThumbnail, item.thumbnailFileId, cover, meta?.wideUrl, meta?.posterUrl)) ?: Tone.background
    val color by androidx.compose.animation.animateColorAsState(tint, com.tmplayer.ui.theme.Motion.standard(), label = "homeArtworkColor")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.20f), Tone.background))))
}
