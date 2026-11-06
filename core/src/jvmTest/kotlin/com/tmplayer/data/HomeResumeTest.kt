package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test

class HomeResumeTest {
    private fun record(id: Long, at: Long, title: String = "Quiet Hills S02E04.mkv", position: Long = 90_000, duration: Long = 2400_000) = ResumeRecord(
        chatId = 1, messageId = id, fileId = id.toInt(), title = title, chatTitle = "Videos", sizeBytes = 900_000_000,
        durationSec = 2400, positionMs = position, durationMs = duration, updatedAt = at, fileName = title)
    @Test fun `recent seven groups episodes and retains completed titles`() {
        val movies = (1L..9L).map { record(it, it * 10, "Title ${('a'.code + it.toInt()).toChar()} 2020.mkv") }
        val seasonOne = record(20, 200, "Quiet Hills S01E01.mkv")
        val seasonSix = record(21, 210, "Quiet Hills S06E20.mkv")
        val watched = WatchedRecord.of(movies.last().toMediaItem(), "Videos", 220, false)
        val history = HomeResume.recent(movies + seasonOne + seasonSix, listOf(watched))
        assertEquals(7, history.size)
        assertEquals(watched.messageId, history.first().messageId)
        assertEquals(1, history.count { it.fileName.orEmpty().startsWith("Quiet Hills") })
        assertEquals(seasonSix.messageId, history[1].messageId)
        assertEquals(history.first().durationMs, history.first().positionMs)
    }
    @Test fun `manually marking an unplayed title does not put it in recent play history`() {
        val played = record(1, 100, "Played 2020.mkv")
        val neverPlayed = WatchedRecord.of(record(2, 200, "Other 2021.mkv").toMediaItem(), "Videos", 300, true)
        assertEquals(listOf(played), HomeResume.recent(listOf(played), listOf(neverPlayed)))
    }
    @Test fun `TG Stream keeps short starts and marks watched only at the end`() {
        val before = TgStreamUi.active
        TgStreamUi.active = true
        try {
            assertTrue(ResumeRules.keeps(5000, 120000))
            assertFalse(ResumeRules.watched(108000, 120000))
            assertTrue(ResumeRules.watched(120000, 120000))
        } finally { TgStreamUi.active = before }
    }
    @Test fun `latest unfinished episode or movie keeps its exact resume identity and position`() {
        val series = record(1, 100)
        val movie = record(2, 200, "Night Train 2020.mkv", position = 420_000)
        assertEquals(movie, HomeResume.latest(listOf(movie, series)))
        assertEquals(series, HomeResume.latest(listOf(movie, series.copy(updatedAt = 300)))?.copy(updatedAt = 100))
        assertEquals(420_000L, HomeResume.latest(listOf(series, movie))?.positionMs)
        assertEquals(movie.fileId, HomeResume.latest(listOf(series, movie))?.toMediaItem()?.fileId)
    }
    @Test fun `finished entries do not replace a title that can still continue`() {
        val unfinished = record(1, 100)
        assertEquals(unfinished, HomeResume.latest(listOf(unfinished, record(2, 200, position = 2400_000))))
        assertNull(HomeResume.latest(emptyList()))
    }
}
