package com.tmplayer.data

/** Repeated channel artwork is never a video identity. */
object TitleIdentity {
    fun parsed(item: MediaItem): ParsedName {
        val renamed = VideoCorrections.current[item.id]?.title
        // Home asks for the same identity several times per row build. MediaItem already caches
        // this exact parse; only a viewer's rename needs a separate answer.
        return if (renamed == null) item.parsedName else MediaName.parse(renamed, item.caption)
    }
    fun name(item: MediaItem): String = SeriesShelf.keyOf(parsed(item).title)
    fun year(item: MediaItem): Int? = parsed(item).year ?: item.parsedName.year
}
