package com.tmplayer.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
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

/** Separate classes get separate workers, keeping Robolectric frame clocks independent. */
abstract class GlassCardsRenderFixture {
    protected fun render(tv: Boolean, name: String, diagnosticCheck: Boolean = false) {
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
                        ShelfTile(entry = ShelfEntry.File(MediaItem(chatId = 1, messageId = 1, fileId = 1, title = "Quiet Hills", fileName = "Quiet.Hills.2020.1080p.mkv", sizeBytes = 900_000_000, durationSec = 7200, mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = 0)), poster = true,
                            watch = remember { SeriesWatch(point = { null }, finished = { false }) }, touch = !tv,
                            chatTitle = { "" }, onPlay = { _, _ -> }, onHold = { _, _ -> }, onOpenSeries = {},
                            modifier = Modifier.width(200.dp))
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
            if (diagnosticCheck) {
                // Start after the Home card was drawn; no refresh or item change may be needed.
                DiagnosticSession.install(RuntimeEnvironment.getApplication())
                DiagnosticSession.start()
                repeat(30) { Thread.sleep(25); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
                val recorded = DiagnosticSession.stop().readText()
                assertTrue(recorded, recorded.contains("HOME_CARD") && recorded.contains("rawName=Quiet.Hills.2020.1080p.mkv"))
                assertTrue(recorded.contains("SESSION_STOP"))
            }
        } finally {
            if (diagnosticCheck) runCatching { DiagnosticSession.stop() }
            controller.pause().stop().destroy(); TgStreamUi.active = false; FormFactor.override(false)
            com.tmplayer.ui.theme.Motion.reduced = false; com.tmplayer.ui.theme.Motion.glass = false
            runBlocking { settings.setThemeChoice(ThemeChoice.System) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardsPhoneTest : GlassCardsRenderFixture() {
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi") fun phone() = render(false, "phone")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardsTelevisionTest : GlassCardsRenderFixture() {
    @Test @Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television-mdpi") fun television() = render(true, "tv")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardsLargeTextTest : GlassCardsRenderFixture() {
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi", fontScale = 1.3f) fun largeText() = render(false, "large-text")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardsRecordingTest : GlassCardsRenderFixture() {
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-port-mdpi")
    fun recordsTheCardAlreadyVisibleWhenRecordingStarts() = render(false, "recording-start", diagnosticCheck = true)
}
