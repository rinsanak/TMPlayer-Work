package com.tmplayer.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.tmplayer.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactControlsTest {
    private fun check(width: Int, height: Int, tv: Boolean, name: String) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_NoActionBar_Fullscreen)
        controller.setup().visible()
        val activity = controller.get()
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val root = FrameLayout(activity).apply { id = R.id.player_root; setBackgroundColor(0xFF20394D.toInt()) }
        LayoutInflater.from(activity).inflate(R.layout.player_controls, root, true)
        activity.setContentView(root)
        var toggles = 0
        val controls = PlayerControls(root, tv, { null }, {}, { toggles++ }, {}, {}, {}, {}, {})
        controls.show()
        val density = activity.resources.displayMetrics.density
        val w = (width * density).toInt(); val h = (height * density).toInt()
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        }
        assertEquals("the window must match the rendered viewport", w, root.width)
        assertEquals("the window must match the rendered viewport", h, root.height)
        val play = root.findViewById<View>(R.id.center_play_pause)
        val bounds = Rect(0, 0, play.width, play.height)
        root.offsetDescendantRectToMyCoords(play, bounds)
        assertTrue("transport must stay at the bottom", bounds.top >= h / 2)
        assertTrue("transport must fit inside the viewport", bounds.left >= 0 && bounds.right <= w && bounds.bottom <= h)
        assertTrue("touch height", play.height >= (48 * density).toInt())
        assertTrue("touch width", play.width >= (48 * density).toInt())
        play.performClick(); assertEquals(1, toggles)
        if (tv) {
            val viewRoot = activity.window.decorView.parent
            ReflectionHelpers.callInstanceMethod<Any>(viewRoot, "ensureTouchMode",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, false))
            ReflectionHelpers.callInstanceMethod<Any>(viewRoot, "windowFocusChanged",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true))
            assertTrue("play must accept remote focus (touch mode=${play.isInTouchMode}, shown=${play.isShown})", play.requestFocus())
            assertTrue(play.isFocused)
            assertEquals(R.id.controls_timebar, play.nextFocusUpId)
            assertEquals(R.id.center_play_pause, root.findViewById<View>(R.id.controls_timebar).nextFocusDownId)
            fun move(from: View, direction: Int, target: View) {
                assertSame("Android focus search must reach the expected control", target, from.focusSearch(direction))
                assertTrue(target.requestFocus(direction))
                assertTrue(target.isFocused)
            }
            val seek = root.findViewById<View>(R.id.controls_timebar)
            move(play, View.FOCUS_UP, seek)
            move(seek, View.FOCUS_DOWN, play)
            play.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            play.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals("remote OK toggles playback once", 2, toggles)
            move(play, View.FOCUS_DOWN, root.findViewById(R.id.control_subtitles))
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/reports/tests/testDebugUnitTest/glass-controls/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        controls.hideNow()
        controller.pause().stop().destroy()
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-port-mdpi")
    fun phonePortrait() = check(360, 720, false, "phone-portrait")

    @Test
    @Config(qualifiers = "w800dp-h450dp-land-mdpi")
    fun phoneLandscape() = check(800, 450, false, "phone-landscape")

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-mdpi")
    fun television() = check(960, 540, true, "television")
}
