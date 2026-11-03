package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    private fun video(id: Long, title: String, duration: Int = 7200) = MediaItem(
        chatId = id, messageId = id, fileId = id.toInt(), title = title, fileName = title,
        sizeBytes = 900_000_000, durationSec = duration, mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = id.toInt(),
    )

    @Test fun `Home excludes clips audio samples and generic documents`() {
        assertFalse(HomeContent.eligible(video(1, "Family trip.mp4", 90)))
        assertFalse(HomeContent.eligible(video(2, "Quiet.Hills.2020.Sample.mp4", 120)))
        assertFalse(HomeContent.eligible(video(3, "Quiet.Hills.2020.m4a").copy(mimeType = "audio/mp4")))
        assertTrue(HomeContent.eligible(video(4, "Quiet.Hills.2020.1080p.mkv")))
        assertTrue(HomeContent.eligible(video(5, "Quiet.Hills.S01E03.mkv", 2400)))
    }

    @Test fun `one title appears once across Home rows`() {
        val first = video(1, "Quiet.Hills.2020.1080p.mkv")
        val other = video(2, "Quiet Hills (2020) 720p.mkv")
        val distinct = video(3, "Harbour.2022.1080p.mkv")
        val rows = HomeContent.clean(listOf(
            HomeRow.Recent(listOf(ShelfEntry.File(first)), loaded = true),
            HomeRow.Chat(2, "Channel", listOf(ShelfEntry.File(other), ShelfEntry.File(distinct)), loaded = true),
        ))
        assertEquals(1, (rows[0] as HomeRow.Recent).entries.size)
        assertEquals(listOf(distinct.id), (rows[1] as HomeRow.Chat).entries.map { (it as ShelfEntry.File).item.id })
    }
}
