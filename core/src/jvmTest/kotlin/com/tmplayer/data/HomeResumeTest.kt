package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test

class HomeResumeTest {
    private fun record(id: Long, at: Long, title: String = "Quiet Hills S02E04.mkv", position: Long = 90_000, duration: Long = 2400_000) = ResumeRecord(
        chatId = 1, messageId = id, fileId = id.toInt(), title = title, chatTitle = "Videos", sizeBytes = 900_000_000,
        durationSec = 2400, positionMs = position, durationMs = duration, updatedAt = at, fileName = title)
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
