package com.tmplayer.data

/** Title-only Home, with copy identity independent of Telegram channel thumbnails. */
object HomeContent {
    fun eligible(item: MediaItem): Boolean {
        if (item.mimeType.startsWith("audio/") || item.extra != null || item.genericFileName) return false
        if (!item.mimeType.startsWith("video/") && item.fileName.substringAfterLast('.').lowercase() !in
            setOf("mkv", "mp4", "m4v", "mov", "avi", "webm", "ts", "m2ts", "mts", "wmv", "flv", "3gp")) return false
        val name = TitleIdentity.parsed(item)
        if (name.extra != null || name.title.isBlank()) return false
        return if (name.isSeries) name.episode != null && (item.durationSec == 0 || item.durationSec >= 300)
        else (SeriesShelf.isFilm(item) || SameTitle.filmKey(item) != null || item.durationSec >= 1200 || SameTitle.onlineKey(item)?.contains(":film:") == true) &&
            (item.durationSec == 0 || item.durationSec >= 1200)
    }
    fun identity(item: MediaItem): String {
        val name = TitleIdentity.parsed(item)
        if (VideoCorrections.current[item.id]?.separate == true) return "separate:${item.id}"
        return if (name.isSeries) "show:${SeriesShelf.keyOf(name.title)}" else "film:${SeriesShelf.keyOf(name.title)}|${name.year}"
    }
    fun clean(rows: List<HomeRow>): List<HomeRow> {
        val files = rows.flatMap { row -> when (row) {
            is HomeRow.Recent -> row.entries.mapNotNull { (it as? ShelfEntry.File)?.item }
            is HomeRow.Chat -> row.entries.mapNotNull { (it as? ShelfEntry.File)?.item }
            is HomeRow.Continue -> row.records.map { it.toMediaItem() }
            else -> emptyList()
        } }.filter(::eligible)
        val years = files.groupBy { TitleIdentity.name(it) }.mapValues { (_, items) -> items.mapNotNull { TitleIdentity.year(it) }.distinct() }
        fun key(item: MediaItem): String {
            val parsed = TitleIdentity.parsed(item)
            if (parsed.isSeries || VideoCorrections.current[item.id]?.separate == true) return identity(item)
            val name = TitleIdentity.name(item)
            val year = TitleIdentity.year(item) ?: years[name]?.singleOrNull()
            return "film:$name|$year"
        }
        val seen = HashMap<String, MutableList<MediaItem>>()
        val fileIds = HashSet<Int>()
        fun take(item: MediaItem): Boolean {
            if (!eligible(item)) return false
            val id = key(item)
            if (item.fileId > 0 && item.fileId in fileIds) return false
            val held = seen[id]
            if (held != null && (TitleIdentity.parsed(item).isSeries || held.any { SameTitle.sameLength(it, item) })) return false
            seen.getOrPut(id) { mutableListOf() }.add(item)
            if (item.fileId > 0) fileIds += item.fileId
            return true
        }
        fun entries(items: List<ShelfEntry>): List<ShelfEntry> {
            val folded = ArrayList<ShelfEntry>()
            val at = HashMap<String, MutableList<Int>>()
            for (entry in items) {
                val item = when (entry) { is ShelfEntry.File -> entry.item; is ShelfEntry.Show -> entry.series.cover }
                val oldIndex = at[key(item)]?.firstOrNull { i ->
                    val old = folded[i]
                    old is ShelfEntry.File && entry is ShelfEntry.File && SameTitle.sameLength(old.item, item)
                }
                val old = oldIndex?.let { folded[it] }
                if (old is ShelfEntry.File && entry is ShelfEntry.File && oldIndex != null) {
                    folded[oldIndex] = ShelfEntry.File(SameTitle.fold(old.item, item))
                } else {
                    at.getOrPut(key(item)) { mutableListOf() }.add(folded.size)
                    folded += entry
                }
            }
            return folded.filter { entry -> take(when (entry) { is ShelfEntry.File -> entry.item; is ShelfEntry.Show -> entry.series.cover }) }
        }
        return rows.mapNotNull { row -> when (row) {
            is HomeRow.Continue -> row.copy(records = row.records.filter { take(it.toMediaItem()) }, more = row.more.filter { eligible(it.toMediaItem()) }).takeIf { it.records.isNotEmpty() }
            is HomeRow.Recent -> row.copy(entries = entries(row.entries)).takeIf { !it.loaded || it.entries.isNotEmpty() }
            is HomeRow.Chat -> row.copy(entries = entries(row.entries), more = row.more.filter(::eligible)).takeIf { !it.loaded || it.entries.isNotEmpty() }
            else -> null
        } }
    }
}
