package com.tmplayer.data

import android.content.Context
import android.util.Base64
import com.tmplayer.App
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.online.OnlineSubtitles
import com.tmplayer.platform.Background
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Same-account cloud preferences. Credentials stay in local Keystore storage on every device.
 * The transport is Telegram Saved Messages (Telegram cloud storage, not end-to-end encryption).
 * Each device writes its own journal, so two devices never overwrite each other's message.
 */
object AccountSettingsSync {
    private const val TAG = "TGSTREAM_ACCOUNT_V1"
    private const val ALIAS = "tgstream.account.sync"
    private var job: Job? = null
    private var openedClient: dev.g000sha256.tdl.TdlClient? = null
    private var openedChat: Long? = null
    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = Background.scope.launch(Dispatchers.IO) {
            val settings = SettingsStore(app)
            val flags = app.getSharedPreferences("account_sync_activation", 0)
            if (!flags.getBoolean("enabled_once", false)) {
                settings.setResumeSync(true)
                flags.edit().putBoolean("enabled_once", true).apply()
            }
            var failures = 0
            var cloudAt = 0L
            try { while (App.inFront) {
                try {
                    if (settings.resumeSyncNow()) {
                        if (android.os.SystemClock.elapsedRealtime() - cloudAt >= 8_000 || cloudAt == 0L) {
                            withTimeout(60_000) { cycle(app, settings) }
                            cloudAt = android.os.SystemClock.elapsedRealtime()
                        }
                        val outcome = ResumeSync.sync(settings)
                        error.value = if (outcome is ResumeSync.Outcome.Failed) "Resume sync did not finish. It will retry automatically." else null
                        failures = 0
                    }
                } catch (e: TimeoutCancellationException) { failures++; error.value = "Account sync timed out. It will retry automatically." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failures++; error.value = "Account sync failed. Check Telegram connection and retry by reopening the app."; com.tmplayer.platform.Logger.w("AccountSettingsSync", "Account sync did not finish (${e.javaClass.simpleName})") }
                delay((2_000L shl failures.coerceAtMost(5)))
            } } finally {
                withContext(NonCancellable) { openedChat?.let { runCatching { openedClient?.closeChat(it) } } }
                openedChat = null; openedClient = null
            }
        }
    }
    private fun allowed(name: String) = name != "resume_sync" &&
        (name in Backups.SETTINGS || Backups.MEMORY.keys.any { name.startsWith(it) } || name in setOf("favorite_chats", "recent_searches"))
    internal suspend fun values(context: Context, settings: SettingsStore): Map<String, Any> = buildMap {
        settings.rawEntries().filterKeys(::allowed).forEach { (k, v) -> put("s:$k", if (k.startsWith("recent_play_") && v is String) {
            val parts = v.split('\n', limit = 3)
            if (parts.size == 3) {
                val description = parts[2].split('\u001F').toMutableList()
                if (description.isNotEmpty()) description[0] = "0"
                if (description.size > 6) description[6] = ""
                parts.take(2).joinToString("\n") + "\n" + description.joinToString("\u001F")
            } else v
        } else v) }
        WatchedStore(context).rawEntries().filterKeys { it.startsWith("w_") }.forEach { (k, v) ->
            val normalised = (v as? String)?.split('\u001F')?.toMutableList()?.also { if (it.size >= 7) it[2] = "0" }?.joinToString("\u001F")
            if (normalised != null) put("w:$k", normalised)
        }
        OnlineMetadata.current?.store?.now?.let {
            put("m:enabled", it.enabled); put("m:skipTimes", it.skipTimes)
            put("m:ownKey", it.ownKey); put("m:omdbKey", it.omdbKey)
        }
        OnlineSubtitles.current?.store?.now?.let {
            put("o:includeMachine", it.includeMachine); put("o:subdlKey", it.subdlKey)
            put("o:msoneShown", it.msoneShown); put("o:msoneAutomatic", it.msoneAutomatic)
        }
        put("g:key", AccountSyncVault.gemini(context))
        put("g:model", context.getSharedPreferences("subtitle_translation", 0).getString("model", "gemini-3.8-flash").orEmpty())
    }
    private fun accepts(name: String): Boolean = when {
        name.startsWith("s:") -> allowed(name.drop(2))
        name.startsWith("w:") -> name.drop(2).matches(Regex("(?:w_|l_)-?[0-9]+_[0-9]+"))
        else -> name in setOf("m:enabled", "m:skipTimes", "m:ownKey", "m:omdbKey", "o:includeMachine", "o:subdlKey", "o:msoneShown", "o:msoneAutomatic", "g:key", "g:model")
    }
    internal suspend fun apply(context: Context, settings: SettingsStore, changes: Map<String, Any?>) {
        val safeSettings = changes.filter { (key, v) -> key.startsWith("s:") && accepts(key) && (v == null || when (Backups.SETTINGS[key.drop(2)] ?: Backups.MEMORY.entries.firstOrNull { key.drop(2).startsWith(it.key) }?.value) {
            Backups.Kind.BOOL -> v is Boolean
            Backups.Kind.LONG -> v is Long
            Backups.Kind.FLOAT -> v is Float
            Backups.Kind.TEXT -> v is String
            null -> key == "s:favorite_chats" && v is Set<*> || key == "s:recent_searches" && v is String
        }) }
        settings.applyAccountValues(safeSettings.mapKeys { it.key.drop(2) })
        // TDLib file identifiers belong to this device. Never import another device's file ID.
        val history = changes.filter { (key, v) -> key.startsWith("w:w_") && accepts(key) && (v == null || v is String) }.mapKeys { it.key.drop(2) }.mapValues { (_, v) ->
            (v as? String)?.split('\u001F')?.toMutableList()?.also { if (it.size >= 7) it[2] = "0" }?.joinToString("\u001F")
        }
        WatchedStore(context).applyAccountValues(history)
        OnlineMetadata.current?.store?.update { old -> old.copy(
            enabled = changes["m:enabled"] as? Boolean ?: old.enabled,
            skipTimes = changes["m:skipTimes"] as? Boolean ?: old.skipTimes,
            ownKey = if ("m:ownKey" in changes) changes["m:ownKey"] as? String ?: "" else old.ownKey,
            omdbKey = if ("m:omdbKey" in changes) changes["m:omdbKey"] as? String ?: "" else old.omdbKey) }
        OnlineSubtitles.current?.store?.update { old -> old.copy(
            includeMachine = changes["o:includeMachine"] as? Boolean ?: old.includeMachine,
            subdlKey = if ("o:subdlKey" in changes) changes["o:subdlKey"] as? String ?: "" else old.subdlKey,
            msoneShown = changes["o:msoneShown"] as? Boolean ?: old.msoneShown,
            msoneAutomatic = changes["o:msoneAutomatic"] as? Boolean ?: old.msoneAutomatic) }
        if ("g:key" in changes) AccountSyncVault.gemini(context, changes["g:key"] as? String ?: "")
        (changes["g:model"] as? String)?.let { context.getSharedPreferences("subtitle_translation", 0).edit().putString("model", it).apply() }
    }
    private data class Line(val id: Long, val text: String)
    private suspend fun lines(td: dev.g000sha256.tdl.TdlClient, chat: Long, query: String): List<Line> {
        val searched = td.searchChatMessages(chat, null, query, null, 0, 0, 100, null).value().messages.filterNotNull()
        val recent = td.getChatHistory(chat, 0, 0, 100, false).value().messages.filterNotNull()
        return (searched + recent).distinctBy { it.id }.mapNotNull {
            if (it.sendingState != null) null else (it.content as? MessageText)?.text?.text?.let { text -> Line(it.id, text) }
        }
    }
    private fun content(text: String) = InputMessageText(FormattedText(text, emptyArray()), LinkPreviewOptions(true, "", false, false, false), false)
    private suspend fun put(td: dev.g000sha256.tdl.TdlClient, chat: Long, existing: Long?, text: String): Long {
        if (existing != null) {
            check(td.editMessageText(chat, existing, null, content(text)).valueOrNull != null) { "Cloud edit failed" }
            return existing
        }
        val options = MessageSendOptions(null, true, false, false, false, 0L, false, null, 0L, 0, false)
        check(td.sendMessage(chat, null, null, options, null, content(text)).valueOrNull != null) { "Cloud send failed" }
        repeat(12) {
            delay(500)
            lines(td, chat, TAG).firstOrNull { it.text == text }?.let { return it.id }
        }
        throw IllegalStateException("Cloud message is still sending")
    }
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun packed(text: String): String {
        val out = ByteArrayOutputStream(); GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        check(out.size() <= 75_000) { "Account settings exceed cloud limit" }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
    private fun unpack(text: String): String {
        check(text.length <= 100_000)
        return GZIPInputStream(ByteArrayInputStream(Base64.decode(text, Base64.NO_WRAP))).use {
            val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = it.read(buffer); if (n < 0) break; check(out.size() + n <= 2_000_000); out.write(buffer, 0, n) }
            out.toString("UTF-8")
        }
    }
    private suspend fun cycle(context: Context, settings: SettingsStore) {
        val td = Td.awaitConnectedSessionOrNull()?.client ?: return
        val account = Td.myId().takeIf { it != 0L } ?: return
        val stateFile = File(context.noBackupFilesDir, "account-sync-$account.json")
        val stored = AccountSyncVault.read(stateFile, ALIAS).takeIf { it.isNotEmpty() }?.let(::JSONObject) ?: JSONObject()
        val actor = stored.optString("actor").ifEmpty { UUID.randomUUID().toString() }
        if (!stored.has("actor")) {
            stored.put("actor", actor)
            AccountSyncVault.write(stateFile, ALIAS, stored.toString())
        }
        var journal = AccountSyncDoc.decode(stored.optString("entries", "{}"))
        val local = values(context, settings)
        val chat = td.createPrivateChat(account, false).valueOrNull?.id ?: return
        if (openedClient !== td || openedChat != chat) {
            openedChat?.let { runCatching { openedClient?.closeChat(it) } }
            td.openChat(chat); openedClient = td; openedChat = chat
        }
        run {
            val manifestLines = lines(td, chat, "$TAG M")
            val manifests = manifestLines.filter { it.text.startsWith("$TAG M ") }.distinctBy { it.text.split(' ').getOrNull(2) }.take(16)
            var remote = emptyMap<String, AccountSyncDoc.Entry>()
            for (manifest in manifests) {
                val h = manifest.text.split(' ')
                if (h.size != 7) continue
                val count = h[4].toIntOrNull()?.takeIf { it in 1..32 } ?: continue
                val chunks = lines(td, chat, "$TAG ${h[2]}").filter { it.text.startsWith("$TAG P ${h[2]} ${h[6]} ${h[3]} ") }
                val parts = (0 until count).map { index -> chunks.firstOrNull { it.text.substringBefore('\n') == "$TAG P ${h[2]} ${h[6]} ${h[3]} $index" }?.text?.substringAfter('\n') }
                check(parts.none { it == null }) { "Cloud snapshot is incomplete" }
                val encoded = parts.joinToString("") { it.orEmpty() }
                check(hash(encoded.toByteArray()) == h[5]) { "Cloud snapshot checksum mismatch" }
                val decoded = runCatching { AccountSyncDoc.decode(unpack(encoded)) }.getOrNull() ?: continue
                remote = AccountSyncDoc.merge(remote, decoded.filterKeys(::accepts))
            }
            // Initial device preferences are a baseline, not edits newer than existing cloud data.
            val first = !stored.has("entries")
            val changed = (local.keys + journal.keys).filter { key -> !AccountSyncDoc.same(local[key], journal[key]?.value) && (local.containsKey(key) || journal.containsKey(key)) }
            for (key in changed) {
                // Retention evictions are not a user's deletion of another device's recent play.
                if (key.startsWith("s:recent_play_") && !local.containsKey(key)) continue
                if (!local.containsKey(key) && !key.startsWith("s:") && !key.startsWith("w:")) continue
                val stamp = if (first && remote.isNotEmpty() || !journal.containsKey(key) && remote.containsKey(key)) 0L else maxOf(System.currentTimeMillis(), (journal[key]?.at ?: 0) + 1)
                journal = journal + (key to AccountSyncDoc.Entry(local[key], stamp, actor))
            }
            val all = AccountSyncDoc.merge(journal, remote)
            val clearedAt = all["s:recent_history_cleared"]?.value as? Long ?: 0L
            val recentKeys = all.mapNotNull { (key, entry) ->
                if (!key.startsWith("s:recent_play_")) return@mapNotNull null
                val parts = (entry.value as? String)?.split('\n', limit = 3) ?: return@mapNotNull null
                if (parts.size != 3) return@mapNotNull null
                val record = ResumeRecord.decode(key.removePrefix("s:recent_play_"), parts[2], parts[0].toLongOrNull() ?: 0, parts[1].toLongOrNull() ?: 0) ?: return@mapNotNull null
                (key to record).takeIf { record.updatedAt > clearedAt }
            }.sortedByDescending { it.second.updatedAt }.distinctBy { HomeContent.identity(it.second.toMediaItem()) }.take(7).mapTo(HashSet()) { it.first }
            val merged = all.filterKeys { !it.startsWith("s:recent_play_") || it in recentKeys }
            val incoming = merged.filter { (key, e) -> !AccountSyncDoc.same(e.value, local[key]) }
            // A setting changed during the network request wins over the remote snapshot.
            val current = values(context, settings)
            val evicted = local.keys.filter { it.startsWith("s:recent_play_") && it !in recentKeys && AccountSyncDoc.same(current[it], local[it]) }.associateWith { null }
            apply(context, settings, incoming.filter { (key, _) -> AccountSyncDoc.same(current[key], local[key]) }.mapValues { it.value.value } + evicted)
            val text = AccountSyncDoc.encode(merged)
            val encoded = packed(text); val digest = hash(encoded.toByteArray())
            val acknowledged = merged.filter { (key, _) -> (local.containsKey(key) || key.startsWith("s:") || key.startsWith("w:")) && AccountSyncDoc.same(current[key], local[key]) }
            stored.put("entries", AccountSyncDoc.encode(acknowledged))
            AccountSyncVault.write(stateFile, ALIAS, stored.toString())
            if (stored.optString("published") != digest) {
                val generation = UUID.randomUUID().toString()
                val slot = 1 - stored.optInt("slot", 0)
                val chunks = encoded.chunked(3200)
                check(chunks.size <= 32)
                val own = lines(td, chat, "$TAG $actor")
                val ids = stored.optJSONObject("slots") ?: JSONObject()
                chunks.forEachIndexed { i, part ->
                    val idKey = "$slot-$i"
                    val id = ids.optLong(idKey, 0).takeIf { it != 0L }
                        ?: own.firstOrNull { it.text.startsWith("$TAG P $actor $slot ") && it.text.substringBefore('\n').substringAfterLast(' ') == i.toString() }?.id
                    ids.put(idKey, put(td, chat, id, "$TAG P $actor $slot $generation $i\n$part"))
                    stored.put("slots", ids)
                    AccountSyncVault.write(stateFile, ALIAS, stored.toString())
                }
                val manifestId = own.firstOrNull { it.text.startsWith("$TAG M $actor ") }?.id
                put(td, chat, manifestId, "$TAG M $actor $generation ${chunks.size} $digest $slot")
                stored.put("published", digest).put("slot", slot).put("slots", ids)
            }
            // Do not acknowledge values skipped because a local edit happened during this cycle.
            stored.put("actor", actor)
            AccountSyncVault.write(stateFile, ALIAS, stored.toString())
        }
    }
}
