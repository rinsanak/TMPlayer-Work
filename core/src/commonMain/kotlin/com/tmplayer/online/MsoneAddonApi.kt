package com.tmplayer.online

import com.tmplayer.data.MediaName
import com.tmplayer.data.ParsedName
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** MSone's public Stremio-compatible addon. No TMPlayer gateway credentials are sent. */
class MsoneAddonApi(
    private val http: HttpTransport,
    private val limiter: RateLimiter,
    private val userAgent: String,
) {
    suspend fun search(target: SubtitleTarget): SearchResult {
        val parsed = MediaName.parse(target.fileName, target.caption)
        if (parsed.extra != null || parsed.lastEpisode != null ||
            (parsed.isSeries && (parsed.season == null || parsed.episode == null))) return SearchResult(emptyList())
        val type = if (parsed.isSeries) "series" else "movie"
        val known = target.imdb?.lowercase(Locale.ROOT)?.takeIf { IMDB.matches(it) }
        val imdb = known ?: run {
            if (parsed.title.length !in 3..120) return SearchResult(emptyList(), SubtitleNotice.MsoneTooShort)
            val catalog = if (parsed.isSeries) "msone-series" else "msone-movies"
            val query = URLEncoder.encode(parsed.title, "UTF-8").replace("+", "%20")
            val answer = get("$BASE/catalog/$type/$catalog/search=$query.json")
                ?: return SearchResult(emptyList(), SubtitleNotice.Offline)
            notice(answer)?.let { return SearchResult(emptyList(), it) }
            catalogId(answer.text, parsed) ?: return SearchResult(emptyList())
        }
        val video = imdb + if (parsed.isSeries) ":${parsed.season}:${parsed.episode}" else ""
        val response = get("$BASE/subtitles/$type/$video.json")
            ?: return SearchResult(emptyList(), SubtitleNotice.Offline)
        notice(response)?.let { return SearchResult(emptyList(), it) }
        return SearchResult(parseHits(response.text, video, parsed))
    }

    suspend fun fetch(hit: SubtitleHit): ByteArray? {
        if (!safeLink(hit.link)) return null
        val response = get(hit.link) ?: return null
        if (response.code !in 200..299 || response.body.isEmpty() || response.body.size > SubtitleText.MAX_BYTES) return null
        val text = SubtitleText.decode(response.body, "ml")
        // An HTML error with status 200 must never be installed as a subtitle.
        if (!TIMECODE.containsMatchIn(text)) return null
        return response.body
    }

    private suspend fun get(url: String): HttpResponse? {
        limiter.acquire()
        // Keep signed links and the video title out of provider logs.
        return try {
            http.send(HttpRequest("GET", url, mapOf("User-Agent" to userAgent, "Accept" to "application/json, text/plain, */*")))
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            null
        }
    }

    private fun notice(response: HttpResponse): SubtitleNotice? = when {
        response.code == 404 -> null
        response.code == 429 -> SubtitleNotice.MsoneBusy
        response.code !in 200..299 -> SubtitleNotice.Offline
        response.body.size > MAX_JSON_BYTES -> SubtitleNotice.Unreadable
        else -> null
    }

    companion object {
        const val BASE = "https://addon.malayalamsubtitles.org"
        private const val MAX_JSON_BYTES = 1024 * 1024
        private val IMDB = Regex("tt\\d{5,10}")
        private val TIMECODE = Regex("\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}")

        internal fun safeLink(link: String): Boolean {
            if (link.length !in 1..4096) return false
            val uri = runCatching { URI(link) }.getOrNull() ?: return false
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            return uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
                (host == "malayalamsubtitles.org" || host.endsWith(".malayalamsubtitles.org"))
        }

        private fun titleKey(title: String) = title.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().removePrefix("the ")

        internal fun catalogId(text: String, parsed: ParsedName): String? {
            if (text.length > MAX_JSON_BYTES) return null
            val metas = runCatching { JSONObject(text).optJSONArray("metas") }.getOrNull() ?: return null
            val ids = (0 until minOf(metas.length(), 100)).mapNotNull { i ->
                val item = metas.optJSONObject(i) ?: return@mapNotNull null
                val id = (item.opt("id") as? String)?.takeIf { IMDB.matches(it) } ?: return@mapNotNull null
                val name = item.opt("name") as? String ?: return@mapNotNull null
                // The official catalog puts the English title, season and Malayalam title in
                // one name, for example "Quiet Hills Season 1 / ... (2020)".
                val title = MediaName.parse(name.substringBefore(" / ").trim()).title
                if (name.length > 500 || titleKey(title) != titleKey(parsed.title)) return@mapNotNull null
                val year = (item.opt("year")?.toString() ?: item.opt("releaseInfo")?.toString())
                    ?.let { Regex("(?:18|19|20)\\d{2}").find(it)?.value?.toIntOrNull() }
                if (parsed.year != null && year != parsed.year) return@mapNotNull null
                id
            }.distinct()
            return ids.singleOrNull()
        }

        internal fun parseHits(text: String, video: String, parsed: ParsedName): List<SubtitleHit> {
            if (text.length > MAX_JSON_BYTES || video.length > 80) return emptyList()
            val hits = runCatching { JSONObject(text).optJSONArray("subtitles") }.getOrNull() ?: return emptyList()
            return (0 until minOf(hits.length(), 100)).mapNotNull { i ->
                val hit = hits.optJSONObject(i) ?: return@mapNotNull null
                val lang = hit.opt("lang") as? String ?: return@mapNotNull null
                if (lang.lowercase(Locale.ROOT) !in setOf("ml", "mal")) return@mapNotNull null
                val id = (hit.opt("id") as? String)?.takeIf { it.isNotBlank() && it.length <= 200 } ?: return@mapNotNull null
                val link = (hit.opt("url") as? String)?.takeIf(::safeLink) ?: return@mapNotNull null
                val label = (hit.opt("label") as? String)?.take(500)?.takeIf { it.isNotBlank() } ?: "${parsed.title}.srt"
                val named = MediaName.parse(label)
                if (parsed.isSeries && (named.season != null && named.season != parsed.season ||
                        named.episode != null && named.episode != parsed.episode || named.lastEpisode != null)) return@mapNotNull null
                SubtitleHit(
                    provider = SubtitleProvider.Msone, id = "addon:$video:$id", language = "ml",
                    release = label, fileName = label.takeIf { it.substringAfterLast('.').lowercase() in SubtitleText.FORMATS } ?: "$label.srt",
                    featureTitle = parsed.title, featureYear = parsed.year,
                    featureSeason = parsed.season, featureEpisode = parsed.episode, link = link,
                )
            }.distinctBy { it.id }
        }

        /** The API query fixes identity; release tags choose the nearest cut among its results. */
        fun automaticHit(target: SubtitleTarget, hits: List<SubtitleHit>): SubtitleHit? {
            val parsed = MediaName.parse(target.fileName, target.caption)
            if (parsed.extra != null || parsed.lastEpisode != null ||
                (parsed.isSeries && (parsed.season == null || parsed.episode == null))) return null
            val words = tokens(target.fileName)
            return hits.filter { it.provider == SubtitleProvider.Msone && it.language in setOf("ml", "mal") &&
                titleKey(it.featureTitle) == titleKey(parsed.title) &&
                (parsed.year == null || it.featureYear == null || it.featureYear == parsed.year) &&
                (!parsed.isSeries || it.featureSeason == parsed.season && it.featureEpisode == parsed.episode) }
                .maxByOrNull { tokens(it.release).count { word -> word in words } }
        }

        private fun tokens(value: String) = value.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }.toSet()
    }
}
