package com.tmplayer.data

/** Repeated channel artwork is never a video identity. */
object TitleIdentity {
    fun parsed(item: MediaItem): ParsedName {
        val raw = VideoCorrections.current[item.id]?.title ?: item.nameForParsing
        // MediaName understands a title attached to a channel handle and series markers.
        // Removing the whole handle first also removed the title embedded in its underscores.
        return MediaName.parse(raw, item.caption)
    }
    fun name(item: MediaItem): String = SeriesShelf.keyOf(parsed(item).title)
    fun year(item: MediaItem): Int? = parsed(item).year ?: item.parsedName.year
}
