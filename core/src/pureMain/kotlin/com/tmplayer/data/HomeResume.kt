package com.tmplayer.data

/** One unfinished title at Home's top. History and the saved record remain unchanged. */
object HomeResume {
    /** Seven distinct titles, including finished titles; episodes of one series share a card. */
    fun recent(records: List<ResumeRecord>, watched: List<WatchedRecord>): List<ResumeRecord> {
        val finished = watched.filter { !it.manual || records.any { r -> r.chatId == it.chatId && r.messageId == it.messageId } }.map { w -> ResumeRecord(w.chatId, w.messageId, w.fileId, w.title, w.chatTitle,
            w.sizeBytes, w.durationSec, w.durationSec.toLong() * 1000, w.durationSec.toLong() * 1000,
            (if (w.manual) records.filter { it.chatId == w.chatId && it.messageId == w.messageId }.maxOf { it.updatedAt } else w.watchedAt), fileName = w.fileName, caption = w.caption) }
        return (records + finished).sortedByDescending { it.updatedAt }
            .distinctBy { HomeContent.identity(it.toMediaItem()) }.take(7)
    }
    fun latest(records: List<ResumeRecord>): ResumeRecord? = records
        .filter { it.positionMs > 0 && (it.durationMs <= 0 || it.positionMs < it.durationMs) }
        .maxByOrNull { it.updatedAt }
}
