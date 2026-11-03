package com.tmplayer.player

/** A short network pause is not proof of a broken media file. */
object StreamRecovery {
    const val OPEN_STALL_MS = 45_000L
    const val REBUFFER_STALL_MS = 60_000L

    fun resumeAt(currentMs: Long?, savedMs: Long): Long =
        (currentMs?.takeIf { it > 0 } ?: savedMs).coerceAtLeast(0)

    fun fileIds(primary: Int, parts: List<Int>): List<Int> = (listOf(primary) + parts).filter { it > 0 }.distinct()

    suspend fun reset(
        ids: List<Int>, position: Long,
        closeReaders: () -> Unit,
        cancel: suspend (Int) -> Unit,
        erase: suspend (Int) -> Unit,
        reopen: suspend (Long) -> Unit,
    ) {
        val files = ids.filter { it > 0 }.distinct()
        closeReaders()
        files.forEach { cancel(it) }
        files.forEach { erase(it) }
        reopen(position.coerceAtLeast(0))
    }
}
