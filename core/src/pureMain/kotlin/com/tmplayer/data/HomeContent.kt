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
    fun clean(rows: List<HomeRow>, online: (MediaItem) -> String? = ::cachedOnlineKey): List<HomeRow> {
        fun originals(entries: List<ShelfEntry>): List<ShelfEntry> = entries.flatMap { entry ->
            if (entry !is ShelfEntry.File || entry.item.versions.isEmpty()) listOf(entry)
            else (listOf(entry.item) + entry.item.versions).distinctBy { it.id }
                .map { ShelfEntry.File(it.copy(versions = emptyList())) }
        }
        // Cached groups are parsed again from the original files. An older parser could fold
        // unrelated channel-prefixed titles into one card and keep that mistake on restart.
        val regrouped = rows.filterNot { TgStreamUi.active && it is HomeRow.Continue }.map { row -> when (row) {
            is HomeRow.Recent -> row.copy(entries = originals(row.entries))
            is HomeRow.Chat -> row.copy(entries = originals(row.entries))
            else -> row
        } }
        val files = regrouped.flatMap { row -> when (row) {
            is HomeRow.Recent -> row.entries.mapNotNull { (it as? ShelfEntry.File)?.item }
            is HomeRow.Chat -> row.entries.mapNotNull { (it as? ShelfEntry.File)?.item }
            is HomeRow.Continue -> row.records.map { it.toMediaItem() }
            else -> emptyList()
        } }.filter(::eligible)
        val years = files.groupBy { TitleIdentity.name(it) }.mapValues { (_, items) -> items.mapNotNull { TitleIdentity.year(it) }.distinct() }
        fun key(item: MediaItem): String {
            val parsed = TitleIdentity.parsed(item)
            if (VideoCorrections.current[item.id]?.separate == true) return identity(item)
            if (parsed.isSeries) return "episode:${TitleIdentity.name(item)}|${parsed.season ?: 1}|${parsed.episode}|${parsed.part}"
            val name = TitleIdentity.name(item)
            val year = TitleIdentity.year(item) ?: years[name]?.singleOrNull()
            return "film:$name|$year|${parsed.part}"
        }
        val seen = HashMap<String, MutableList<MediaItem>>()
        val fileIds = HashSet<Int>()
        fun take(item: MediaItem, id: String = key(item)): Boolean {
            if (!eligible(item)) return false
            if (item.fileId > 0 && item.fileId in fileIds) return false
            val held = seen[id]
            if (held != null && (TitleIdentity.parsed(item).isSeries || held.any { SameTitle.sameLength(it, item) })) return false
            seen.getOrPut(id) { mutableListOf() }.add(item)
            if (item.fileId > 0) fileIds += item.fileId
            return true
        }
        fun foldEntries(items: List<ShelfEntry>): List<ShelfEntry> {
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
            return folded
        }
        // Reuse the upstream identity graph BEFORE applying Home's missing-year fallback.
        // Unpacking a cached group and comparing names alone split verified alias copies again.
        val known = HashMap<String, String?>()
        val allEntries = regrouped.flatMap { row -> when (row) {
            is HomeRow.Recent -> row.entries
            is HomeRow.Chat -> row.entries
            else -> emptyList()
        } }
        val raw = allEntries.flatMap { entry -> when (entry) {
            is ShelfEntry.File -> listOf(entry.item).filter(::eligible)
            is ShelfEntry.Show -> entry.series.episodes.flatMap { it.copies }
        } }.distinctBy { it.id }.sortedWith(FeedMerge.newestFirst)
        val upstream = SameTitle.dedupe(SeriesShelf.arrange(raw)) { item ->
            known.getOrPut(item.id) { if (VideoCorrections.current[item.id]?.separate == true) null else online(item) }
        }
        val canonical = foldEntries(upstream.filterIsInstance<ShelfEntry.File>()) + upstream.filterIsInstance<ShelfEntry.Show>()
        val byCopy = HashMap<String, ShelfEntry>()
        canonical.forEach { entry -> when (entry) {
            is ShelfEntry.File -> {
                byCopy[entry.item.id] = entry
                entry.item.versions.forEach { byCopy[it.id] = entry }
            }
            is ShelfEntry.Show -> entry.series.episodes.forEach { episode -> episode.copies.forEach { byCopy[it.id] = entry } }
        } }
        fun entries(items: List<ShelfEntry>): List<ShelfEntry> = items.mapNotNull { entry ->
            val ids = when (entry) {
                is ShelfEntry.File -> listOf(entry.item.id)
                is ShelfEntry.Show -> entry.series.episodes.flatMap { episode -> episode.copies.map { it.id } }
            }
            val rebuilt = ids.firstNotNullOfOrNull { byCopy[it] } ?: entry
            when (rebuilt) {
                is ShelfEntry.File -> rebuilt.takeIf { take(it.item) }
                is ShelfEntry.Show -> rebuilt.takeIf { take(it.series.cover, "show:${it.series.key}") }
            }
        }
        return regrouped.mapNotNull { row -> when (row) {
            is HomeRow.Continue -> row.copy(records = row.records.filter { take(it.toMediaItem()) }, more = row.more.filter { eligible(it.toMediaItem()) }).takeIf { it.records.isNotEmpty() }
            is HomeRow.Recent -> row.copy(entries = entries(row.entries)).takeIf { !it.loaded || it.entries.isNotEmpty() }
            is HomeRow.Chat -> row.copy(entries = entries(row.entries), more = row.more.filter(::eligible)).takeIf { !it.loaded || it.entries.isNotEmpty() }
            else -> null
        } }
    }
    private fun cachedOnlineKey(item: MediaItem): String? = runCatching {
        if (VideoCorrections.current[item.id]?.separate == true) return@runCatching null
        SameTitle.onlineKey(item)?.let { return@runCatching it }
        val query = item.metaQuery ?: return@runCatching null
        val info = com.tmplayer.online.OnlineMetadata.current?.cachedMatch(query) ?: return@runCatching null
        "${info.provider.name.lowercase()}:${if (info.kind == com.tmplayer.online.MetaKind.Show) "show" else "film"}:${info.id}"
    }.getOrNull()
}
