package com.tmplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSubtitlesTest {
    @Test fun `only small text subtitle documents are offered`() {
        for (name in listOf("Show.S01E01.ml.srt", "FILM.SRT", "x.ass", "x.ssa", "x.vtt", "x.sub")) {
            assertTrue(name, ChatSubtitles.accepts(name, 1234))
        }
        for (name in listOf("x.mkv", "x.zip", "x.srt.exe", "x.srt.txt", "srt", "x.srt/other")) {
            assertFalse(name, ChatSubtitles.accepts(name, 1234))
        }
        assertTrue(ChatSubtitles.accepts("x.srt", ChatSubtitles.MAX_BYTES))
        assertFalse(ChatSubtitles.accepts("x.srt", ChatSubtitles.MAX_BYTES + 1))
        assertFalse(ChatSubtitles.accepts("x.srt", -1))
        assertFalse(ChatSubtitles.accepts("x.srt", 0))
    }
}
