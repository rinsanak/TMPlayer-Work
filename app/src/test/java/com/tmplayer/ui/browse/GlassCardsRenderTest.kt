package com.tmplayer.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.tmplayer.data.*
import com.tmplayer.i18n.L
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardsRenderTest {
    private fun render(tv: Boolean, name: String) {
        val settings = SettingsStore(RuntimeEnvironment.getApplication())
        TgStreamUi.active = true; FormFactor.override(tv); com.tmplayer.ui.theme.Motion.reduced = true
        runBlocking { settings.setThemeChoice(ThemeChoice.Glass) }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var markDrawn = false
        var glassDrawn = false
        try {
            activity.setContent {
                TMPlayerTheme {
                    val glass = com.tmplayer.ui.theme.LocalGlassTheme.current
                    androidx.compose.runtime.SideEffect { glassDrawn = glass }
                    Column(Modifier.fillMaxSize().background(Tone.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhoneHomeBrand(Modifier.fillMaxWidth(), false)
                        com.tmplayer.ui.components.BrandLockup(modifier = Modifier.size(0.dp).graphicsLayer { alpha = 0f }, mark = { markDrawn = true })
                        MediaCard(MediaItem(chatId = 1, messageId = 1, fileId = 1, title = "Quiet Hills", fileName = "Quiet.Hills.2020.1080p.mkv", sizeBytes = 900_000_000, durationSec = 7200, mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = 0), watched = null, onClick = {}, poster = true, artworkOnly = true, modifier = Modifier.width(200.dp))
                    }
                }
            }
            repeat(30) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
            val deadline = System.currentTimeMillis() + 3000
            while (!glassDrawn && System.currentTimeMillis() < deadline) {
                Thread.sleep(25); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            }
            assertTrue("saved Glass appearance must be drawn", glassDrawn)
            val compose = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
            val owner = compose.getChildAt(0) as androidx.compose.ui.node.RootForTest
            val texts = mutableListOf<String>()
            fun walk(node: SemanticsNode) { node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }; node.children.forEach(::walk) }
            walk(owner.semanticsOwner.unmergedRootSemanticsNode)
            assertTrue(texts.toString(), texts.any { "Quiet Hills" in it })
            assertTrue(texts.toString(), texts.any { "2020" in it })
            assertTrue(texts.toString(), texts.contains(L.logsSessionStart)); assertTrue(texts.toString(), texts.contains(L.logsSessionStop))
            assertFalse("in-app branding must not draw an app icon", markDrawn)
            val root = activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
            val file = File("build/reports/tests/testDebugUnitTest/glass-app/$name.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        } finally {
            controller.pause().stop().destroy(); TgStreamUi.active = false; FormFactor.override(false)
            com.tmplayer.ui.theme.Motion.reduced = false; com.tmplayer.ui.theme.Motion.glass = false
            runBlocking { settings.setThemeChoice(ThemeChoice.System) }
        }
    }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi") fun phone() = render(false, "phone")
    @Test @Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television-mdpi") fun television() = render(true, "tv")
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi", fontScale = 1.3f) fun largeText() = render(false, "large-text")
}
