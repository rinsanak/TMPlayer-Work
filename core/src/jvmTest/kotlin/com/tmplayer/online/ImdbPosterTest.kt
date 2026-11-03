package com.tmplayer.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ImdbPosterTest {
    private val answer = """{"d":[{"id":"nm1234567","l":"Don't Trouble the Trouble","y":2026,"qid":"name"},{"id":"tt31841170","l":"Don't Trouble the Trouble","y":2026,"qid":"movie","i":{"imageUrl":"https://m.media-amazon.com/images/M/poster@._V1_.jpg"}}]}"""

    @Test fun `missing TMDB key uses exact title and year and persists the real poster`() = runBlocking {
        val dir = Files.createTempDirectory("poster-fallback").toFile()
        try {
            var requests = 0
            val http = HttpTransport { request ->
                requests++
                assertTrue(request.url.startsWith("https://v3.sg.media-imdb.com/suggestion/"))
                HttpResponse(200, body = answer.toByteArray())
            }
            val store = MetadataStore(java.io.File(dir, "settings"))
            store.update { it.copy(enabled = true) }
            val cache = MetadataCache(java.io.File(dir, "cache"))
            fun online() = OnlineMetadata("", store, cache, "test", http = http, sleep = {})
            val query = MetaQuery.of("RTDont.Trouble.the.Trouble.2026.720p.mkv")!!
            val info = (online().lookup(query) as MetaResult.Found).info
            assertEquals(MetaProvider.Imdb, info.provider)
            assertEquals("tt31841170", info.id)
            assertEquals("https://m.media-amazon.com/images/M/poster@._V1_QL75_UX500_.jpg", info.posterUrl)
            assertEquals(info, (online().lookup(query) as MetaResult.Found).info)
            assertEquals(1, requests)
            assertFalse(query.cacheKey("en-US").endsWith("+title2"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun `wrong year different title people and unsafe images never become a poster`() = runBlocking {
        val dir = Files.createTempDirectory("poster-refusal").toFile()
        try {
            val store = MetadataStore(java.io.File(dir, "settings"))
            store.update { it.copy(enabled = true) }
            for (body in listOf(answer.replace("2026", "1990"), answer.replace("the Trouble", "the River"), answer.replace("\"movie\"", "\"tvSeries\""), answer.replace("m.media-amazon.com", "untrusted.example"))) {
                val online = OnlineMetadata("", store, MetadataCache(Files.createTempDirectory(dir.toPath(), "cache").toFile()), "test", http = HttpTransport { HttpResponse(200, body = body.toByteArray()) }, sleep = {})
                assertFalse(online.lookup(MetaQuery("Dont Trouble the Trouble", year = 2026)) is MetaResult.Found)
            }
        } finally { dir.deleteRecursively() }
    }
}
