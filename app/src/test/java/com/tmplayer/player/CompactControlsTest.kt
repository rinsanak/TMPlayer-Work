package com.tmplayer.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
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
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactControlsTest {
    private fun check(width: Int, height: Int, tv: Boolean, name: String) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
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
        val play = root.findViewById<View>(R.id.center_play_pause)
        val bounds = Rect(0, 0, play.width, play.height)
        root.offsetDescendantRectToMyCoords(play, bounds)
        assertTrue("transport must stay at the bottom", bounds.top >= h / 2)
        assertTrue("touch height", play.height >= (48 * density).toInt())
        assertTrue("touch width", play.width >= (48 * density).toInt())
        play.performClick(); assertEquals(1, toggles)
        if (tv) {
            play.requestFocus(); assertTrue(play.isFocused)
            assertEquals(R.id.controls_timebar, play.nextFocusUpId)
            assertEquals(R.id.center_play_pause, root.findViewById<View>(R.id.controls_timebar).nextFocusDownId)
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

    @Test fun phonePortrait() = check(360, 720, false, "phone-portrait")
    @Test fun phoneLandscape() = check(800, 450, false, "phone-landscape")
    @Test fun television() = check(960, 540, true, "television")
}
