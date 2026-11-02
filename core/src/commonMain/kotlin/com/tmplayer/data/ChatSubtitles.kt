package com.tmplayer.data

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/** Subtitle documents from the playing video's chat, fetched only when the viewer asks. */
object ChatSubtitles {
    const val MAX_BYTES = 4L * 1024 * 1024
    const val PAGE_SIZE = 100
    private val extensions = setOf("srt", "ass", "ssa", "vtt", "sub")

    data class Entry(val chatId: Long, val messageId: Long, val fileId: Int, val name: String, val bytes: Long)
    data class Page(val items: List<Entry>, val next: Long)

    fun accepts(name: String, bytes: Long): Boolean =
        name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in extensions && bytes in 1..MAX_BYTES

    suspend fun page(td: TdlClient, chatId: Long, query: String, from: Long = 0): Page = withContext(Dispatchers.IO) {
        if (chatId == 0L) return@withContext Page(emptyList(), 0)
        withTimeout(20_000) {
            val answer = td.searchChatMessages(chatId, null, query.trim().take(200), null, from, 0, PAGE_SIZE, SearchMessagesFilterDocument())
            val found = answer.valueOrNull ?: throw IOException(answer.errorMessage ?: "Telegram subtitle search failed")
            val items = found.messages.asSequence().filter { it.chatId == chatId && !MediaMapper.selfDestructs(it) && it.schedulingState == null && it.sendingState == null }
                .mapNotNull { message ->
                    val doc = (message.content as? MessageDocument)?.document ?: return@mapNotNull null
                    val bytes = maxOf(doc.document.size, doc.document.expectedSize)
                    if (!accepts(doc.fileName, bytes)) return@mapNotNull null
                    Entry(chatId, message.id, doc.document.id, doc.fileName, bytes)
                }.distinctBy { it.messageId }.toList()
            Page(items, found.nextFromMessageId.takeUnless { it == from || found.messages.isEmpty() } ?: 0)
        }
    }

    class NoSpace(message: String) : IOException(message)

    suspend fun download(td: TdlClient, chatId: Long, entry: Entry, availableBytes: Long): File = withContext(Dispatchers.IO) {
        if (entry.chatId != chatId || !accepts(entry.name, entry.bytes)) throw IOException("Invalid subtitle document")
        val needed = entry.bytes + MAX_BYTES + 65_536
        if (availableBytes < needed) throw NoSpace(com.tmplayer.i18n.L.storageDriveTooSmall(StorageRelocationPlan.size(availableBytes.coerceAtLeast(0)), StorageRelocationPlan.size(needed)))
        withTimeout(30_000) {
            val answer = td.downloadFile(entry.fileId, 16, 0, 0, true)
            val fetched = answer.valueOrNull ?: throw IOException(answer.errorMessage ?: "Telegram subtitle download failed")
            val local = fetched.local
            val file = File(local.path)
            if (!local.isDownloadingCompleted || !file.isFile || file.length() !in 1..MAX_BYTES) {
                throw IOException("Subtitle file is incomplete or too large")
            }
            file
        }
    }
}
