package com.tmplayer.data

import com.tmplayer.platform.JobFailures
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.MessageSendingStateFailed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/** Only called by Stop Log. Owns the upload across Settings navigation and rotation. */
internal object DiagnosticUpload {
    private val scope = JobFailures.scope("diagnostic upload", Dispatchers.IO)
    private val mutex = Mutex()
    private val attempts = linkedMapOf<String, Deferred<Result<Unit>>>()

    suspend fun send(file: File) {
        val session = withTimeout(5_000) { Td.awaitAuthorizedSession() }
        val td = session.client
        val account = withTimeout(5_000) { td.getMe().valueOrNull?.id }
            ?.takeIf { it != 0L } ?: throw IOException("No signed-in account")
        if (!session.isCurrent()) throw IOException("Account changed")
        val key = "$account:${file.canonicalPath}"
        val attempt = mutex.withLock {
            attempts[key] ?: scope.async {
                try {
                    withTimeout(90_000) {
                        if (!file.isFile || file.length() == 0L) throw IOException("Diagnostic export missing")
                        val chat = td.createPrivateChat(account, false).valueOrNull?.id
                            ?: throw IOException("Saved Messages unavailable")
                        coroutineScope {
                            // Subscribe before sendMessage: even an immediate acknowledgement is retained.
                            val outcomes = Channel<Pair<Long, Boolean>>(64)
                            val succeeded = launch(start = CoroutineStart.UNDISPATCHED) {
                                td.messageSendSucceededUpdates.collect {
                                    if (it.message.chatId == chat) outcomes.send(it.oldMessageId to true)
                                }
                            }
                            val failed = launch(start = CoroutineStart.UNDISPATCHED) {
                                td.messageSendFailedUpdates.collect {
                                    if (it.message.chatId == chat) outcomes.send(it.oldMessageId to false)
                                }
                            }
                            try {
                                if (!session.isCurrent()) throw IOException("Account changed")
                                val options = MessageSendOptions(null, true, false, false, false, 0L, false, null, 0L, 0, false)
                                val content = InputMessageDocument(InputDocument(InputFileLocal(file.absolutePath), null, true), null)
                                val message = td.sendMessage(chat, null, null, options, null, content).valueOrNull
                                    ?: throw IOException("Diagnostic send rejected")
                                if (message.sendingState is MessageSendingStateFailed) throw IOException("Diagnostic send failed")
                                if (message.sendingState != null) {
                                    awaitDiagnosticConfirmation(message.id, outcomes)
                                }
                                if (!session.isCurrent()) throw IOException("Account changed")
                            } finally {
                                succeeded.cancel(); failed.cancel(); outcomes.close()
                            }
                        }
                    }
                    Result.success(Unit)
                } catch (e: TimeoutCancellationException) {
                    // A timeout can leave TDLib's send pending. Keep this attempt rather than
                    // silently sending a duplicate when Stop is pressed again. The ZIP stays local.
                    Result.failure(IOException("Upload not confirmed; export retained", e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.also { task ->
                attempts[key] = task
                // Old completed sessions only. Pending uploads are never evicted or restarted.
                while (attempts.size > 16) {
                    val old = attempts.entries.firstOrNull { it.key != key && it.value.isCompleted } ?: break
                    attempts.remove(old.key)
                }
            }
        }
        attempt.await().getOrThrow()
    }
}

internal suspend fun awaitDiagnosticConfirmation(messageId: Long, outcomes: ReceiveChannel<Pair<Long, Boolean>>) {
    while (true) {
        val (id, ok) = outcomes.receive()
        if (id != messageId) continue
        if (!ok) throw IOException("Diagnostic upload failed; export retained")
        return
    }
}
