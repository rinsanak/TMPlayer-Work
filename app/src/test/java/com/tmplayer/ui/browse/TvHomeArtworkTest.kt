package com.tmplayer.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tmplayer.data.FormFactor
import com.tmplayer.data.MediaItem
import com.tmplayer.data.Thumbnails
import com.tmplayer.ui.components.Cover
import com.tmplayer.ui.components.EdgeBackdrop
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h320dp-land-night-xhdpi")
class TvHomeArtworkTest {
    private val item = MediaItem(chatId = 1, messageId = 1, fileId = 1,
        title = "Harbour.S01E02.mkv", fileName = "Harbour.S01E02.mkv", sizeBytes = 900_000_000,
        durationSec = 2400, mimeType = "video/x-matroska", thumbnailFileId = 77,
        miniThumbnail = byteArrayOf(1), date = 1)
    private val cover = Cover("Harbour", null, 0)

    @Test fun `matched artwork excludes channel thumbnail but unmatched keeps it`() {
        val matched = tvHomeBackdropSource(item, cover, null, "https://example.invalid/poster.jpg")
        assertNull(matched.miniThumbnail)
        assertEquals(0, matched.thumbnailFileId)
        val unmatched = tvHomeBackdropSource(item, cover, null, null)
        assertArrayEquals(item.miniThumbnail, unmatched.miniThumbnail)
        assertEquals(77, unmatched.thumbnailFileId)
    }

    @Test fun `small online poster survives and TV ground has no solid colour panel`() {
        FormFactor.override(true)
        Motion.reduced = true
        val poster = File.createTempFile("tv-home-poster", ".png")
        val image = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.GREEN)
        poster.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val url = "https://example.invalid/tv-home-small-poster.png"
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            runBlocking { assertNotNull(Thumbnails.online(url, 780) { poster }) }
            controller.get().setContent {
                TMPlayerTheme {
                    Box(Modifier.fillMaxSize().background(Tone.background)) {
                        EdgeBackdrop(tvHomeBackdropSource(item, cover, null, url), Modifier.fillMaxSize())
                    }
                }
            }
            repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
            val root = controller.get().window.decorView
            val output = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(output))
            val left = output.getPixel(output.width / 10, output.height / 3)
            val right = output.getPixel(output.width * 95 / 100, output.height / 3)
            assertTrue("title area stays dark rather than a solid poster tint", Color.green(left) < 100)
            assertTrue("online poster is visible rather than rejected for its width", Color.green(right) > Color.red(right) + 80)
            val out = File("../build/device-test/review/tv-home-artwork.png")
            out.parentFile.mkdirs()
            out.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            controller.pause().stop().destroy()
            poster.delete()
            FormFactor.override(false)
            Motion.reduced = false
        }
    }
}
