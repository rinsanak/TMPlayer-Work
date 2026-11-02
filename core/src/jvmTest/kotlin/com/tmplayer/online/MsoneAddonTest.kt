package com.tmplayer.online

import com.tmplayer.data.MediaName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MsoneAddonTest {
    private val movie = SubtitleTarget("Quiet.Hills.2020.1080p.BluRay.mkv", 900_000_000, imdb = "tt1234567")
    private val episode = SubtitleTarget("Quiet.Hills.S01E02.1080p.WEB.mkv", 800_000_000, imdb = "tt1234567")
    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nമലയാളം\n".toByteArray()
    private val link = "https://addon.malayalamsubtitles.org/subfiles/tt1234567/s1/video.srt?sig=public-link"

    private fun result(label: String = "Quiet.Hills.S01E02.1080p.WEB.srt", url: String = link) =
        """{"subtitles":[{"id":"file1","lang":"mal","label":"$label","url":"$url"}]}"""

    @Test fun `series search uses the exact episode without a gateway key`() = runBlocking {
        val requests = mutableListOf<HttpRequest>()
        val api = MsoneAddonApi(HttpTransport {
            requests += it
            HttpResponse(200, body = result().toByteArray())
        }, RateLimiter(0, { 0L }, {}), "TGStream test")
        val found = api.search(episode)
        assertEquals(1, found.hits.size)
        assertTrue(requests.single().url.endsWith("/subtitles/series/tt1234567:1:2.json"))
        assertFalse(requests.single().headers.containsKey("X-Api-Key"))
        assertEquals(2, found.hits.single().featureEpisode)
    }

    @Test fun `an exact unique catalog title and year resolve a movie`() = runBlocking {
        var calls = 0
        val api = MsoneAddonApi(HttpTransport {
            calls++
            HttpResponse(200, body = (if ("/catalog/" in it.url)
                """{"metas":[{"id":"tt1234567","name":"Quiet Hills / മലയാളം (2020)","releaseInfo":"2020"}]}"""
            else result("Quiet.Hills.2020.1080p.BluRay.srt")).toByteArray())
        }, RateLimiter(0, { 0L }, {}), "test")
        assertEquals(1, api.search(movie.copy(imdb = null)).hits.size)
        assertEquals(2, calls)
    }

    @Test fun `ambiguous titles remakes and incomplete episode names are not guessed`() = runBlocking {
        val parsed = MediaName.parse(movie.fileName)
        assertNull(MsoneAddonApi.catalogId("""{"metas":[{"id":"tt1234567","name":"Quiet Hills","year":1990}]}""", parsed))
        assertNull(MsoneAddonApi.catalogId("""{"metas":[{"id":"tt1234567","name":"Quiet Hills"},{"id":"tt7654321","name":"Quiet Hills"}]}""", parsed.copy(year = null)))
        val api = MsoneAddonApi(HttpTransport { error("must not request a season pack") }, RateLimiter(0, { 0L }, {}), "test")
        assertTrue(api.search(episode.copy(fileName = "Quiet.Hills.S01.mkv")).hits.isEmpty())
        assertTrue(api.search(episode.copy(fileName = "Quiet.Hills.S01E02E03.mkv")).hits.isEmpty())
    }

    @Test fun `bad links wrong language and wrong episodes are excluded`() {
        val parsed = MediaName.parse(episode.fileName)
        for (unsafe in listOf("http://malayalamsubtitles.org/a", "https://evil.example/a", "https://malayalamsubtitles.org.evil.test/a", "https://user@malayalamsubtitles.org/a", "https://malayalamsubtitles.org:8000/a")) {
            assertTrue(MsoneAddonApi.parseHits(result(url = unsafe), "tt1234567:1:2", parsed).isEmpty())
        }
        assertTrue(MsoneAddonApi.parseHits(result("Quiet.Hills.S02E02.srt"), "tt1234567:1:2", parsed).isEmpty())
        assertTrue(MsoneAddonApi.parseHits(result().replace("\"mal\"", "\"eng\""), "tt1234567:1:2", parsed).isEmpty())
        assertTrue(MsoneAddonApi.safeLink(link))
    }

    @Test fun `automatic choice prefers the same release and refuses another episode`() {
        val parsed = MediaName.parse(episode.fileName)
        val web = MsoneAddonApi.parseHits(result(), "tt1234567:1:2", parsed).single()
        val bluray = web.copy(id = "other", release = "Quiet.Hills.S01E02.BluRay.srt")
        assertEquals(web, MsoneAddonApi.automaticHit(episode, listOf(bluray, web)))
        assertNull(MsoneAddonApi.automaticHit(episode, listOf(web.copy(featureEpisode = 3))))
        assertNull(MsoneAddonApi.automaticHit(episode, listOf(web.copy(featureTitle = "Another Show"))))
        val unnamed = MsoneAddonApi.parseHits(result("Quiet.Hills.srt"), "tt1234567:1:2", parsed)
        assertNull(MsoneAddonApi.automaticHit(episode, unnamed))
    }

    @Test fun `HTML empty oversized expired and rate limited answers do not become subtitles`() = runBlocking {
        val hit = MsoneAddonApi.parseHits(result(), "tt1234567:1:2", MediaName.parse(episode.fileName)).single()
        for ((code, bytes) in listOf(200 to "<html>Blocked</html>".toByteArray(), 200 to ByteArray(0), 403 to srt, 200 to ByteArray(SubtitleText.MAX_BYTES + 1))) {
            val api = MsoneAddonApi(HttpTransport { HttpResponse(code, body = bytes) }, RateLimiter(0, { 0L }, {}), "test")
            assertNull(api.fetch(hit))
        }
        val api = MsoneAddonApi(HttpTransport { HttpResponse(429) }, RateLimiter(0, { 0L }, {}), "test")
        assertEquals(SubtitleNotice.MsoneBusy, api.search(episode).notice)
    }

    @Test fun `downloaded Malayalam is cached and remains usable without the network`() = runBlocking {
        val dir = Files.createTempDirectory("tg-msone-addon").toFile()
        try {
            var calls = 0
            val online = OnlineSubtitles("", OnlineSubtitlesStore(File(dir, "account")), SubtitleCache(File(dir, "cache")), "test",
                http = HttpTransport {
                    calls++
                    HttpResponse(200, body = if ("/subtitles/" in it.url) result().toByteArray() else srt)
                }, sleep = {}, directMsone = true)
            assertTrue(online.msoneShown)
            val hit = online.searchMsone(episode).hits.single()
            val loaded = online.download(hit, episode) as DownloadResult.Done
            assertTrue(loaded.file.readText().contains("മലയാളം"))
            assertTrue((online.download(hit, episode) as DownloadResult.Done).fromCache)
            assertTrue(online.searchMsone(episode).fromCache)
            assertEquals(2, calls)
            online.store.update { it.copy(msoneAutomatic = false) }
            assertFalse(OnlineSubtitlesStore(File(dir, "account")).now.msoneAutomatic)
        } finally { dir.deleteRecursively() }
    }
}
