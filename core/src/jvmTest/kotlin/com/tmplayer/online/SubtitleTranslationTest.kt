package com.tmplayer.online

import org.junit.Assert.*
import org.junit.Test

class SubtitleTranslationTest {
    @Test fun `SRT and VTT translation keeps timestamps cue identifiers and original document`() {
        for ((ext, input) in listOf("srt" to "1\n00:00:01,000 --> 00:00:03,000\nHello\nSecond line\n\n2\n00:00:04,000 --> 00:00:06,000\nGoodbye\n",
            "vtt" to "WEBVTT\n\ncue-a\n00:01.000 --> 00:03.000 align:start\nHello\nSecond line\n\ncue-b\n00:04.000 --> 00:06.000\nGoodbye\n")) {
            val doc = SubtitleTranslation.parse(input, ext)!!
            val result = doc.translated(listOf("നമസ്കാരം", "രണ്ടാം വരി", "വിട"))
            assertEquals(input.lines().filter { it.contains("-->") }, result.lines().filter { it.contains("-->") })
            assertEquals(doc.lines.size, result.lines().size)
            assertEquals("Hello", doc.dialogue.first().text)
            assertTrue(result.contains("നമസ്കാരം"))
            if (ext == "vtt") { assertTrue(result.startsWith("WEBVTT")); assertTrue(result.contains("cue-a")) }
        }
    }
    @Test fun `ASS dialogue translation preserves event columns and timing`() {
        val input = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello, friend"
        val doc = SubtitleTranslation.parse(input, "ass")!!
        assertEquals("Hello, friend", doc.dialogue.single().text)
        assertTrue(doc.translated(listOf("സുഹൃത്തേ")).endsWith(",,സുഹൃത്തേ"))
        assertEquals(input.substringBeforeLast(",,Hello"), doc.translated(listOf("സുഹൃത്തേ")).substringBeforeLast(",,സു"))
    }
    @Test fun `incomplete translation and unsupported data are rejected`() {
        assertNull(SubtitleTranslation.parse("not subtitles", "srt"))
        assertNull(SubtitleTranslation.parse("1\n00:00 --> 00:01\nx", "sub"))
        val doc = SubtitleTranslation.parse("1\n00:00:00,000 --> 00:00:01,000\nHello", "srt")!!
        assertTrue(runCatching { doc.translated(emptyList()) }.isFailure)
    }
}
