package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    @Test fun `video channel RT marks regroup cached copies without merging remakes`() {
        val names = listOf("Avarachan.and.Sons.2026.1080p.mkv", "@EverMovies RTAvarachan.and.Sons.2026.720p.mkv", "RT Avarachan & Sons 2026 720p.mkv", "www.example.org - RT_Avarachan.and.Sons.2026.Tamil.mkv")
        val copies = names.mapIndexed { i, name -> video(300L + i, name) }
        val remake = video(310, "Avarachan.and.Sons.2001.1080p.mkv")
        val cached = copies.first().copy(versions = copies.take(2))
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(cached)) + (copies.drop(2) + remake).map { ShelfEntry.File(it) }, true)))
        val entries = (rows.single() as HomeRow.Recent).entries.map { (it as ShelfEntry.File).item }
        assertEquals(2, entries.size)
        assertEquals(copies.map { it.id }.toSet(), entries.first { TitleIdentity.year(it) == 2026 }.versions.map { it.id }.toSet())
    }

    @Test fun `copies from later Home rows are retained in the first card`() {
        val a = video(31, "Quiet.Hills.2020.1080p.mkv")
        val b = video(32, "Quiet.Hills.2020.720p.Tamil.mkv")
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(a)), true), HomeRow.Chat(32, "Other", listOf(ShelfEntry.File(b)), true)))
        val entry = (rows.single() as HomeRow.Recent).entries.single() as ShelfEntry.File
        assertEquals(setOf(a.id, b.id), entry.item.versions.map { it.id }.toSet())
    }

    @Test fun `cached episode copies rebuild as a series without collapsing seasons`() {
        val a = video(41, "Quiet.Hills.S01E01.mkv", 2400)
        val b = video(42, "Quiet.Hills.S02E01.mkv", 2400)
        val cached = a.copy(versions = listOf(a, b))
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(cached)), true)))
        val show = ((rows.single() as HomeRow.Recent).entries.single() as ShelfEntry.Show).series
        assertEquals(listOf(1, 2), show.seasons.map { it.number })
        assertEquals(2, show.episodeCount)
    }

    @Test fun `cached verified aliases reuse original dedupe on the first Home build`() {
        val dir = java.nio.file.Files.createTempDirectory("home-alias").toFile()
        val before = com.tmplayer.online.OnlineMetadata.current
        try {
            val cache = com.tmplayer.online.MetadataCache(java.io.File(dir, "cache"))
            val store = com.tmplayer.online.MetadataStore(java.io.File(dir, "settings"))
            store.update { it.copy(enabled = true) }
            val online = com.tmplayer.online.OnlineMetadata("", store, cache, "test", http = com.tmplayer.online.HttpTransport { throw AssertionError("No network for cached identity") }, language = { "en" })
            com.tmplayer.online.OnlineMetadata.current = online
            val a = video(51, "Quiet.Hills.2020.1080p.mkv")
            val b = video(52, "Montagnes.Calmes.2020.720p.mkv")
            val info = com.tmplayer.online.MetaInfo(com.tmplayer.online.MetaProvider.Tmdb, "42", com.tmplayer.online.MetaKind.Film, "Quiet Hills", year = 2020)
            for (item in listOf(a, b)) cache.putAnswer(item.metaQuery!!.cacheKey(online.tmdbLanguage()), com.tmplayer.online.MetaResult.Found(info))
            val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(a.copy(versions = listOf(a, b)))), true)))
            val entry = (rows.single() as HomeRow.Recent).entries.single() as ShelfEntry.File
            assertEquals(setOf(a.id, b.id), entry.item.versions.map { it.id }.toSet())
        } finally {
            com.tmplayer.online.OnlineMetadata.current = before
            dir.deleteRecursively()
        }
    }

    @Test fun `RT uploader mark and ampersand copies from the screenshot form one card`() {
        val names = listOf("Avarachan.and.Sons.2026.1080p.mkv", "RTAvarachan.and.Sons.2026.720p.mkv", "RTAvarachan.&.Sons.2026.1080p.mkv")
        val copies = names.mapIndexed { i, name -> video((i + 21).toLong(), name) }
        val rows = HomeContent.clean(listOf(HomeRow.Recent(copies.map { ShelfEntry.File(it) }, true)))
        val entries = (rows.single() as HomeRow.Recent).entries
        assertEquals(1, entries.size)
        assertEquals(3, (entries.single() as ShelfEntry.File).item.versions.size)
        assertEquals("Don't Trouble the Trouble", com.tmplayer.online.MetaQuery.of("RTDon't.Trouble.the.Trouble.2026.1080p.mkv")?.title)
        assertEquals(com.tmplayer.online.MetaMatch.normalise("Don't Trouble the Trouble"), com.tmplayer.online.MetaMatch.normalise("Dont Trouble the Trouble"))
    }
    @Test fun `attached channel handles keep the title and fold its quality copies`() {
        val one = video(11, "@CC_Quiet_Hills_2020_1080p.mkv")
        val two = video(12, "Quiet.Hills.2020.720p.mkv")
        assertEquals("Quiet Hills", TitleIdentity.parsed(one).title)
        assertEquals("Quiet Hills", com.tmplayer.online.MetaQuery.of(one.fileName)?.title)
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(one), ShelfEntry.File(two)), true)))
        val entries = (rows.single() as HomeRow.Recent).entries
        assertEquals(1, entries.size)
        assertEquals(2, (entries.single() as ShelfEntry.File).item.versions.size)
    }

    @Test fun `cached groups are rebuilt without losing distinct original titles`() {
        val one = video(11, "@CC_Quiet_Hills_2020_1080p.mkv")
        val two = video(12, "@CC_Harbour_2022_720p.mkv")
        val cached = one.copy(versions = listOf(one, two))
        val rows = HomeContent.clean(listOf(HomeRow.Recent(listOf(ShelfEntry.File(cached)), true)))
        assertEquals(2, (rows.single() as HomeRow.Recent).entries.size)
    }
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
