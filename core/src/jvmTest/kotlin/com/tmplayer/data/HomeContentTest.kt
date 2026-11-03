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

    @Test fun `channel tags quality variants and a missing year share one title and keep copies`() {
        val one = video(1, "@channel Quiet.Hills.2020.1080p.mkv")
        val two = video(2, "[Uploader] Quiet.Hills.720p.mkv")
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(one), ShelfEntry.File(two)), true)))
        val entries = (rows.single() as HomeRow.Recent).entries
        assertEquals(1, entries.size)
        assertEquals(2, (entries.single() as ShelfEntry.File).item.versions.size)
    }
    @Test fun `same thumbnail is not identity and remake years remain separate`() {
        val one = video(1, "Quiet.Hills.1990.mkv").copy(thumbnailFileId = 77)
        val two = video(2, "Quiet.Hills.2020.mkv").copy(thumbnailFileId = 77)
        val third = video(3, "Harbour.2022.mkv").copy(thumbnailFileId = 77)
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(one,two,third).map { ShelfEntry.File(it) }, true)))
        assertEquals(3, (rows.single() as HomeRow.Recent).entries.size)
    }
}
