package com.tmplayer.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tmplayer.data.*
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.TMPlayerTheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-night-mdpi")
class ContinueHomeTest {
    @Test fun `finished recent titles still render Continue and ask for all seven posters on TV`() {
        FormFactor.override(true); Motion.television = true; Motion.reduced = true
        val rowsShown = mutableSetOf<String>()
        val posters = mutableSetOf<Long>()
        val records = (1L..7L).map { id -> ResumeRecord(1, id, 0, "Title ${('a'.code + id.toInt()).toChar()} 2020.mkv", "Videos", 900_000_000, 7200, 7200_000, 7200_000, id, fileName = "Title ${('a'.code + id.toInt()).toChar()} 2020.mkv") }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {
                TMPlayerTheme {
                    HomePane(rows = emptyList(), recentHistory = records, rowsReady = false,
                        watch = SeriesWatch(point = { null }, finished = { true }), art = emptyMap(), chatTitle = { "Videos" },
                        start = 20.dp, end = 20.dp, bottom = 12.dp,
                        onRowShown = { rowsShown += it.key }, onLastArtWanted = { posters += it.messageId },
                        onPlay = { _, _ -> }, onHoldMedia = { _, _ -> }, onOpenChat = {})
                }
            }
            repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
            assertTrue("finished history must not fall through to the empty library screen", "continue" in rowsShown)
            assertEquals((1L..7L).toSet(), posters)
            val root = controller.get().window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val output = File("../build/device-test/review/continue-seven-tv.png")
            output.parentFile.mkdirs(); output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            controller.pause().stop().destroy()
            FormFactor.override(false); Motion.television = false; Motion.reduced = false
        }
    }
}
