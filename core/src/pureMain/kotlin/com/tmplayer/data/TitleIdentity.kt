package com.tmplayer.data

/** Repeated channel artwork is never a video identity. */
object TitleIdentity {
    private val handle = Regex("""@[^\s.]+""")
    private val brackets = Regex("""\[(?!\d{4}\])[^\]\r\n]{1,100}\]""")
    fun parsed(item: MediaItem): ParsedName {
        val raw = VideoCorrections.current[item.id]?.title ?: item.nameForParsing
        val clean = handle.replace(brackets.replace(raw, " "), " ").trim(' ', '.', '-', '_')
        return MediaName.parse(clean.ifBlank { raw }, item.caption)
    }
    fun name(item: MediaItem): String = SeriesShelf.keyOf(parsed(item).title)
    fun year(item: MediaItem): Int? = parsed(item).year ?: item.parsedName.year
}
