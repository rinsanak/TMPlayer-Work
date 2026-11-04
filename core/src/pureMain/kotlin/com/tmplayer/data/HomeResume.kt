package com.tmplayer.data

/** One unfinished title at Home's top. History and the saved record remain unchanged. */
object HomeResume {
    fun latest(records: List<ResumeRecord>): ResumeRecord? = records
        .filter { it.positionMs > 0 && (it.durationMs <= 0 || it.positionMs < it.durationMs) }
        .maxByOrNull { it.updatedAt }
}
