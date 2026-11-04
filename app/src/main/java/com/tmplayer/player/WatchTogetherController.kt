package com.tmplayer.player

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tmplayer.data.MediaItem
import com.tmplayer.data.WatchRelay
import com.tmplayer.data.guardedLifecycleScope as lifecycleScope
import com.tmplayer.i18n.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Same-account, cross-network playback control. No video or Telegram credentials leave the device. */
@androidx.media3.common.util.UnstableApi
class WatchTogetherController(private val activity: FragmentActivity, private val player: () -> ExoPlayer?,
    private val media: () -> MediaItem, private val say: (String) -> Unit) {
    private var relay: WatchRelay? = null
    private var client: WatchSync.Client? = null
    private var job: Job? = null
    private var event: Job? = null
    private var ignoreUntil = 0L
    private val lock = Mutex()
    private fun text(key: String) = L.messages.text("player.$key")
    private fun snapshot(): WatchSync.Snapshot? = player()?.let { WatchSync.Snapshot(it.currentPosition,
        it.playWhenReady && it.playbackState == Player.STATE_READY, it.duration.coerceAtLeast(0)) }
    private fun identity(): String { val item = media(); return WatchSync.identity(item.fileName.ifBlank { item.title }, item.sizeBytes, item.durationSec) }
    fun open() {
        if (job?.isActive == true && client == null) { say(text("watch_connecting")); return }
        val choices = if (client == null) arrayOf(text("watch_create"), text("watch_join")) else arrayOf(text("watch_code"), text("watch_leave"))
        AlertDialog.Builder(activity).setTitle(L.playerWatchTogether).setMessage(text("watch_explanation"))
            .setPositiveButton(if (client == null) choices[0] else choices[1]) { _, _ -> if (client == null) start(null) else leave() }
            .setNeutralButton(if (client == null) choices[1] else choices[0]) { _, _ ->
                if (client != null) showCode(client?.let { it.code ?: it.room }.orEmpty())
                else {
                    val input = EditText(activity).apply { hint = text("watch_code"); setSingleLine(); setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER) }
                    AlertDialog.Builder(activity).setTitle(text("watch_join")).setView(input)
                        .setPositiveButton(text("watch_join")) { _, _ -> start(input.text.toString().trim().lowercase()) }
                        .setNegativeButton(L.commonCancel, null).show()
                }
            }.setNegativeButton(L.commonCancel, null).show()
    }
    private fun showCode(room: String) {
        AlertDialog.Builder(activity).setTitle(text("watch_code")).setMessage(room)
            .setPositiveButton(text("watch_copy")) { _, _ ->
                (activity.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText(text("watch_code"), room))
            }.setNegativeButton(L.commonClose, null).show()
    }
    private fun start(join: String?) {
        if (join != null && !WatchSync.validRoom(join) && !WatchSync.validCode(join)) { say(text("watch_unavailable")); return }
        val initial = snapshot() ?: return
        val mediaKey = identity()
        var code = join ?: WatchSync.newCode()
        val room = UUID.randomUUID().toString().replace("-", "")
        val actor = UUID.randomUUID().toString()
        say(text("watch_connecting"))
        job = activity.lifecycleScope.launch {
            try {
                val opened = if (join != null) WatchRelay.connect(code) else {
                    var created: WatchRelay? = null
                    repeat(5) {
                        if (created == null) try {
                            created = WatchRelay.connect(code, WatchSync.Frame(room, mediaKey, actor,
                                initial.position, initial.playing, System.currentTimeMillis(), UUID.randomUUID().toString(), initial.duration, code))
                        } catch (e: IllegalStateException) {
                            if (e.message != "watch_code_in_use") throw e
                            code = WatchSync.newCode()
                        }
                    }
                    created ?: throw IllegalStateException("watch_unavailable")
                }
                relay = opened
                val source = opened.read() ?: throw IllegalStateException("watch_unavailable")
                check(source.media == mediaKey) { "watch_mismatch" }
                if (identity() != mediaKey) throw IllegalStateException("watch_mismatch")
                val connected = WatchSync.Client(source.room, mediaKey, opened, actor, source.code)
                client = connected
                if (join == null) { connected.publish(initial); showCode(source.code ?: source.room) }
                say(text("watch_connected"))
                var heartbeat = SystemClock.elapsedRealtime()
                while (true) {
                    if (identity() != mediaKey) throw IllegalStateException("watch_mismatch")
                    lock.withLock {
                        snapshot()?.let { state ->
                            connected.poll(state)?.let { adjustment ->
                                ignoreUntil = SystemClock.elapsedRealtime() + 1500
                                adjustment.position?.takeIf { player()?.playbackState != Player.STATE_BUFFERING }?.let { player()?.seekTo(it) }
                                player()?.playWhenReady = adjustment.playing
                            }
                            if (connected.mine && SystemClock.elapsedRealtime() - heartbeat >= 10_000) {
                                connected.publish(state); heartbeat = SystemClock.elapsedRealtime()
                            }
                        }
                    }
                    delay(2000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { say(text(if (e.message == "watch_mismatch") "watch_mismatch" else if (e.message == "watch_expired") "watch_expired" else "watch_unavailable")) }
            finally {
                event?.cancel(); event = null
                client = null
                val old = relay; relay = null
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { old?.close() } }
            }
        }
    }
    fun bufferingChanged() { if (client?.mine == true) changed() }
    fun changed() {
        if (client == null || SystemClock.elapsedRealtime() < ignoreUntil) return
        event?.cancel()
        event = activity.lifecycleScope.launch {
            delay(300)
            try { lock.withLock { snapshot()?.let { client?.publish(it) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { say(text("watch_unavailable")); leave() }
        }
    }
    fun leave() { event?.cancel(); job?.cancel(); job = null; client = null }
}
