package com.tmplayer.ui.components

import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.tmplayer.data.FormFactor
import com.tmplayer.ui.browse.underTvBar
import com.tmplayer.ui.connect.ConnectBannerView
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * HX22: on a TV, the connection banner floats over the page without taking the remote, and the
 * remote still reaches it: Up from the bar goes into it, OK presses Retry, Down and Back put the
 * remote back on the bar's item, and when the banner goes away while it has the remote, the
 * remote goes back too. A card that asks (the language notice's kind) takes the remote, keeps it
 * from the arrows, and gives it back to the page's item it was on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-night-xhdpi", application = android.app.Application::class)
abstract class TvReachFixture {

    private var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        FormFactor.override(false)
    }

    protected lateinit var activity: ComponentActivity
    protected var focused: String? = null
    protected var retried = 0
    protected var proxies = 0
    protected var bannerShown by mutableStateOf(true)
    protected var cardShown by mutableStateOf(false)
    protected val tile = List(3) { FocusRequester() }

    protected fun Modifier.named(name: String) = onFocusChanged { if (it.isFocused) focused = name else if (focused == name) focused = null }

    protected fun compose(glass: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            com.tmplayer.data.SettingsStore(org.robolectric.RuntimeEnvironment.getApplication()).setThemeChoice(
                if (glass) com.tmplayer.data.ThemeChoice.Glass else com.tmplayer.data.ThemeChoice.System)
        }
        var glassDrawn = false
        FormFactor.override(true)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = requireNotNull(controller).get()
        activity.setContent {
            TMPlayerTheme {
                val isGlass = com.tmplayer.ui.theme.LocalGlassTheme.current
                androidx.compose.runtime.SideEffect { glassDrawn = isGlass }
                Box(Modifier.fillMaxSize().background(Tone.background).tvReachRoot()) {
                    val bar = remember { FocusRequester() }
                    val chats = remember { FocusRequester() }
                    val content = remember { FocusRequester() }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.padding(start = 48.dp, top = 27.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(80.dp, 40.dp).focusRequester(bar).tvReturnPoint(bar).named("home").focusable())
                            Box(Modifier.size(80.dp, 40.dp).focusRequester(chats).tvReturnPoint(chats).named("chats").focusable())
                        }
                        Row(
                            Modifier.padding(start = 48.dp, top = 80.dp).underTvBar(content, bar),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            tile.forEachIndexed { i, req ->
                                Box(Modifier.size(160.dp, 90.dp).focusRequester(req).named("tile$i").focusable())
                            }
                        }
                    }
                    if (bannerShown) {
                        ConnectBannerView(
                            text = "Telegram may be blocked on this network.",
                            proxyOn = false,
                            onRetry = { retried++ },
                            onOpenProxies = { proxies++ },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 48.dp).widthIn(max = 440.dp),
                        )
                    }
                    if (cardShown) {
                        val keep = remember { FocusRequester() }
                        Row(Modifier.align(Alignment.BottomEnd).padding(40.dp).tvHoldsFocus(keep), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(80.dp, 40.dp).named("change").focusable())
                            Box(Modifier.size(80.dp, 40.dp).focusRequester(keep).named("keep").focusable())
                        }
                    }
                }
            }
        }
        idle()
        if (glass) {
            val deadline = System.currentTimeMillis() + 3000
            while (!glassDrawn && System.currentTimeMillis() < deadline) { Thread.sleep(25); idle() }
            assertTrue("Glass appearance must be active for the Glass focus case", glassDrawn)
        }
        val viewRoot = activity.window.decorView.parent
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Any>(
            viewRoot, "ensureTouchMode",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Any>(
            viewRoot, "windowFocusChanged",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true),
        )
        activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).requestFocus()
        idle()
    }





    protected fun key(code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        idle()
    }

    protected fun idle() = repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
}

// One focus scenario per native worker; retain every navigation assertion.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-night-xhdpi")
class TvReachBannerTest : TvReachFixture() {
    @Test
    fun bannerIsReachedFromTheBarAndGivesTheRemoteBack() {
        compose()
        tile[1].requestFocus()
        idle()
        assertEquals("tile1", focused)

        // Up from the page goes to the bar, as it did before the banner: the banner never takes it on the way.
        key(KeyEvent.KEYCODE_DPAD_UP)
        val barItem = focused
        assertTrue("on the bar: $barItem", barItem == "home" || barItem == "chats")

        // Up from the bar: into the banner, on Retry. OK presses it, and the remote stays.
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(null, focused)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, retried)
        assertEquals(null, focused)

        // Right walks to Use a proxy; Right again at the banner's edge goes back, never to the page behind.
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, proxies)
        assertEquals(1, retried)

        // Down: back on the bar's item it came from.
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(barItem, focused)

        // From Home this time; Back puts the remote back there.
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("home", focused)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(null, focused)
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals("home", focused)

        // From Chats: Left at the banner's start goes back too, never to the page behind.
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("chats", focused)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(null, focused)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("chats", focused)

        // The banner goes away while it has the remote: the remote goes back to Chats, never nowhere.
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(null, focused)
        bannerShown = false
        idle()
        assertEquals("chats", focused)

        // No banner: Up from the bar does nothing, as before.
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("chats", focused)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-night-xhdpi")
class TvReachCardTest : TvReachFixture() {
    @Test
    fun askingCardHoldsTheRemoteAndGivesItBackToThePage() {
        compose()
        bannerShown = false
        idle()
        tile[2].requestFocus()
        idle()
        assertEquals("tile2", focused)

        cardShown = true
        idle()
        assertEquals("keep", focused)
        // The arrows stay inside the card: Up does not reach the page behind it.
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("keep", focused)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("change", focused)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("change", focused)

        // Answered: the remote is back on the tile it was on.
        cardShown = false
        idle()
        assertEquals("tile2", focused)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-night-xhdpi")
class TvReachGlassCardTest : TvReachFixture() {
    @Test
    fun askingCardHoldsTheRemoteAndGivesItBackToThePage() {
        compose(glass = true)
        bannerShown = false
        idle()
        tile[2].requestFocus()
        idle()
        assertEquals("tile2", focused)

        cardShown = true
        idle()
        assertEquals("keep", focused)
        // The arrows stay inside the card: Up does not reach the page behind it.
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("keep", focused)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("change", focused)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("change", focused)

        // Answered: the remote is back on the tile it was on.
        cardShown = false
        idle()
        assertEquals("tile2", focused)
    }
}
