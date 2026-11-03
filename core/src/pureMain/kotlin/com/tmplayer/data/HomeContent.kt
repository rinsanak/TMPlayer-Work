package com.tmplayer.data

/** Android Home is a title collection, not a list of every video document in Telegram. */
object HomeContent {
    fun eligible(item: MediaItem): Boolean {
        if (item.mimeType.startsWith("audio/") || item.extra != null || item.genericFileName) return false
        if (!item.mimeType.startsWith("video/") && item.fileName.substringAfterLast('.').lowercase() !in
            setOf("mkv", "mp4", "m4v", "mov", "avi", "webm", "ts", "m2ts", "mts", "wmv", "flv", "3gp")) return false
        val name = SeriesShelf.episodeOf(item)
        if (name.extra != null || name.title.isBlank()) return false
        return if (name.isSeries) name.episode != null && (item.durationSec == 0 || item.durationSec >= 300)
        else (SeriesShelf.isFilm(item) || SameTitle.filmKey(item) != null || SameTitle.onlineKey(item)?.contains(":film:") == true) &&
            (item.durationSec == 0 || item.durationSec >= 1200)
    }

    fun identity(item: MediaItem): String {
        val name = SeriesShelf.episodeOf(item)
        return if (name.isSeries) "show:${SeriesShelf.keyOf(name.title)}"
        else SameTitle.filmKey(item)?.substringBefore("|part") ?: SameTitle.onlineKey(item) ?: "film:${SeriesShelf.keyOf(name.title)}|${name.year}"
    }

    fun clean(rows: List<HomeRow>): List<HomeRow> {
        val seen = HashSet<String>()
        fun take(item: MediaItem): Boolean {
            if (!eligible(item)) return false
            val keys = listOfNotNull(identity(item), SameTitle.onlineKey(item))
            if (keys.any { it in seen }) return false
            seen.addAll(keys)
            return true
        }
        fun entries(items: List<ShelfEntry>) = SameTitle.dedupe(items).filter { entry ->
            when (entry) {
                is ShelfEntry.File -> take(entry.item)
                is ShelfEntry.Show -> take(entry.series.cover)
            }
        }
        return rows.mapNotNull { row ->
            when (row) {
                is HomeRow.Continue -> row.copy(records = row.records.filter { take(it.toMediaItem()) },
                    more = row.more.filter { eligible(it.toMediaItem()) }).takeIf { it.records.isNotEmpty() }
                is HomeRow.Recent -> row.copy(entries = entries(row.entries)).takeIf { !it.loaded || it.entries.isNotEmpty() }
                is HomeRow.Chat -> row.copy(entries = entries(row.entries), more = row.more.filter(::eligible))
                    .takeIf { !it.loaded || it.entries.isNotEmpty() }
                // The separate Watch later and new-episode pages stay available from navigation.
                else -> null
            }
        }
    }
}
