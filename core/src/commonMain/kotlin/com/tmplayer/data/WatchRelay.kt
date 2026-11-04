package com.tmplayer.data

import com.tmplayer.player.WatchSync
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.LinkPreviewOptions
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.MessageText
import kotlinx.coroutines.delay

/** Explicitly joined, same-account session in Saved Messages. Never carries media or credentials. */
class WatchRelay private constructor(private val td: dev.g000sha256.tdl.TdlClient,
    private val chat: Long, private val message: Long) : WatchSync.Transport {
    override suspend fun read(): WatchSync.Frame? = WatchSync.decode(
        (td.getMessage(chat, message).valueOrNull?.content as? MessageText)?.text?.text)
    override suspend fun write(frame: WatchSync.Frame) {
        val result = td.editMessageText(chat, message, null, content(WatchSync.encode(frame)))
        check(result.valueOrNull != null) { "watch_unavailable" }
    }
    suspend fun close() { td.closeChat(chat) }
    companion object {
        private fun content(text: String) = InputMessageText(FormattedText(text, emptyArray()),
            LinkPreviewOptions(true, "", false, false, false), false)
        suspend fun connect(room: String, initial: WatchSync.Frame? = null): WatchRelay {
            check(room.matches(Regex("[a-f0-9]{32}"))) { "watch_unavailable" }
            val td = Td.awaitAuthorizedSession().client
            val mine = Td.myId().takeIf { it != 0L } ?: throw IllegalStateException("watch_unavailable")
            val chat = td.createPrivateChat(mine, false).valueOrNull?.id ?: mine
            td.openChat(chat)
            try {
                if (initial != null) {
                    val options = MessageSendOptions(null, true, false, false, false, 0L, false, null, 0L, 0, false)
                    check(td.sendMessage(chat, null, null, options, null, content(WatchSync.encode(initial))).valueOrNull != null) { "watch_unavailable" }
                }
                repeat(8) {
                    val messages = td.getChatHistory(chat, 0, 0, 100, false).valueOrNull?.messages.orEmpty()
                    val found = messages.filterNotNull().firstOrNull { msg ->
                        msg.sendingState == null && WatchSync.decode((msg.content as? MessageText)?.text?.text)?.room == room
                    }
                    if (found != null) return WatchRelay(td, chat, found.id)
                    if (initial == null && it == 0) {
                        val foundBySearch = td.searchChatMessages(chat, null, room, null, 0, 0, 10, null).valueOrNull?.messages
                            ?.filterNotNull()?.firstOrNull { msg -> WatchSync.decode((msg.content as? MessageText)?.text?.text)?.room == room }
                        if (foundBySearch != null) return WatchRelay(td, chat, foundBySearch.id)
                    }
                    delay(750)
                }
                throw IllegalStateException("watch_unavailable")
            } catch (e: Exception) { td.closeChat(chat); throw e }
        }
    }
}
