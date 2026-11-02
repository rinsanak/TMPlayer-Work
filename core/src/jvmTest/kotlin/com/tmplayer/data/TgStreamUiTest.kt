package com.tmplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TgStreamUiTest {
    @Test fun `Android selection hides removed controls and keeps chosen dependencies`() {
        val before = TgStreamUi.active
        try {
            TgStreamUi.active = true
            assertFalse(TgStreamUi.shows("H008"))
            assertFalse(TgStreamUi.shows("S11-001"))
            assertFalse(TgStreamUi.shows("C004"))
            assertTrue(TgStreamUi.shows("N011"))
            assertTrue(TgStreamUi.shows("S11-004"))
            assertTrue(TgStreamUi.shows("T007"))
            assertTrue(TgStreamUi.shows("P015"))
            assertFalse(TgStreamUi.playerEntry("Stereo"))
            assertFalse(TgStreamUi.playerEntry("SleepTimer"))
            assertTrue(TgStreamUi.playerEntry("OpenInAnotherApp"))
            TgStreamUi.active = false
            assertTrue(TgStreamUi.shows("H008"))
            assertTrue(TgStreamUi.playerEntry("Stereo"))
        } finally { TgStreamUi.active = before }
    }
}
