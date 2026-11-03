package com.tmplayer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tmplayer.data.FormFactor
import kotlinx.coroutines.launch
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * The hold to scroll handle on a phone, composed for real and drawn to PNGs under
 * `build/device-test/review/`: out after a scroll, held under a finger (the accent fill), and
 * dragged with its bubble, and the list moved by the drag.
 */
// One case per class: Gradle gives each class its own process (forkEvery), and after one render
// test in a process Robolectric's frame clock stops delivering frames, so a second case in the
// same class never saw the handle come out.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
abstract class FastScrollerRenderTest {

    private var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>? = null

    @org.junit.After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        com.tmplayer.ui.theme.Motion.reduced = false
    }

    private fun idle(ms: Long) = repeat((ms / 50).toInt()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }

    protected fun render(out: String, glass: Boolean = false) {
        // Component fixtures choose their appearance explicitly, independently of app migrations.
        kotlinx.coroutines.runBlocking {
            com.tmplayer.data.SettingsStore(org.robolectric.RuntimeEnvironment.getApplication()).setThemeChoice(
                if (glass) com.tmplayer.data.ThemeChoice.Glass else com.tmplayer.data.ThemeChoice.System)
        }
        // The answer is kept for the process, and a television test before this one leaves it set.
        FormFactor.override(false)
        // The handle's slide and fade land at once: what is checked is where it stands, and in a
        // long run of render tests an animation's frames did not always come before the picture.
        com.tmplayer.ui.theme.Motion.reduced = true
        val state = LazyListState(firstVisibleItemIndex = 60)
        var accent = 0
        var ground = 0
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = requireNotNull(controller).get()
        var glassDrawn = false
        activity.setContent {
            TMPlayerTheme {
                val isGlass = com.tmplayer.ui.theme.LocalGlassTheme.current
                androidx.compose.runtime.SideEffect { glassDrawn = isGlass }
                // A fixed opaque fixture behind Glass keeps pixel comparisons local to the handle.
                val fixtureGround = if (glass) androidx.compose.ui.graphics.Color(0xFF203040) else Tone.background
                accent = Tone.accent.toArgb()
                ground = fixtureGround.toArgb()
                Box(Modifier.fillMaxSize().background(fixtureGround)) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        items(400, key = { it }) { i ->
                            // Words only on the start side, so the end edge is the page's own colour but for the handle.
                            Text("Video ${i + 1}", color = Tone.text, modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, top = 20.dp))
                        }
                    }
                    FastScroller(state, label = { key -> "Episode ${(key as Int) + 1}" })
                }
            }
        }
        idle(100)
        if (glass) {
            val deadline = System.currentTimeMillis() + 3000
            while (!glassDrawn && System.currentTimeMillis() < deadline) { Thread.sleep(25); idle(50) }
            assertTrue("Glass appearance must be active for the Glass scroll case", glassDrawn)
        }
        // The list is held mid-scroll until the picture is taken, which brings the handle out.
        // Started from the test, and held rather than a short scroll: after a long run of render
        // tests the handle's own watcher started late, after a 200 ms scroll had already ended,
        // and the handle never came (CI, 2026-10-22).
        val moving = activity.lifecycleScope.launch {
            state.scroll {
                scrollBy(1f)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        idle(800)
        val root = activity.window.decorView
        val rest = draw(activity, "fast-scroller-rest-$out")
        moving.cancel()
        // The handle is what stands out from the page down the last pixels of the end edge (the
        // left one in Arabic).
        fun run(x: Int) = (0 until rest.height).filter { y -> rest.getPixel(x, y) != ground }
        val x = if (out == "rtl") 6 else rest.width - 6
        val rows = run(x)
        assertTrue("nothing on the other edge", run(if (out == "rtl") rest.width - 6 else 6).isEmpty())
        assertTrue("the handle is out after a scroll", rows.size > 40)
        val y = (rows.first() + rows.last()) / 2f
        val start = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x.toFloat(), y, 0))
        idle(300)
        val held = draw(activity, "fast-scroller-held-$out")
        assertTrue("held, it fills with the accent", held.getPixel(x, y.toInt()) == accent)
        val before = state.firstVisibleItemIndex
        for (step in 1..10) {
            root.dispatchTouchEvent(MotionEvent.obtain(start, start + step * 16L, MotionEvent.ACTION_MOVE, x.toFloat(), y + step * 40f, 0))
            idle(50)
        }
        idle(300)
        draw(activity, "fast-scroller-bubble-$out")
        assertTrue("the drag moved the list (${state.firstVisibleItemIndex} from $before)", state.firstVisibleItemIndex > before + 80)
        root.dispatchTouchEvent(MotionEvent.obtain(start, start + 400, MotionEvent.ACTION_UP, x.toFloat(), y + 400f, 0))
        idle(300)
    }

    private fun draw(activity: ComponentActivity, out: String): Bitmap {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("../build/device-test/review/$out.png").absoluteFile
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}

class FastScrollerRenderDarkTest : FastScrollerRenderTest() {
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun `phone, dark`() = render("dark")
}

class FastScrollerRenderLightTest : FastScrollerRenderTest() {
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun `phone, light`() = render("light")
}

class FastScrollerRenderRtlTest : FastScrollerRenderTest() {
    @Test
    @Config(sdk = [35], qualifiers = "ar-w411dp-h891dp-port-notnight-xhdpi")
    fun `phone, right to left`() = render("rtl")
}

class FastScrollerRenderGlassTest : FastScrollerRenderTest() {
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun `phone, glass`() = render("glass", glass = true)
}
