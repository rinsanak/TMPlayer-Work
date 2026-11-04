package com.tmplayer.player

import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

/** Control state only. Each device opens and authorizes its own media. */
object WatchSync {
    private val codeRandom = java.security.SecureRandom()
    fun newCode(): String = (1000 + codeRandom.nextInt(9000)).toString()
    fun validRoom(room: String): Boolean = room.matches(Regex("[a-f0-9]{32}"))
    fun validCode(code: String): Boolean = code.matches(Regex("[1-9][0-9]{3}"))
    fun active(frame: Frame, now: Long = System.currentTimeMillis()): Boolean = abs(now - frame.at) <= 120_000

    data class Frame(val room: String, val media: String, val writer: String, val position: Long,
        val playing: Boolean, val at: Long, val revision: String, val duration: Long, val code: String? = null)
    data class Snapshot(val position: Long, val playing: Boolean, val duration: Long)
    data class Adjustment(val position: Long?, val playing: Boolean)
    interface Transport {
        suspend fun read(): Frame?
        suspend fun write(frame: Frame)
    }
    class Client(val room: String, val media: String, private val transport: Transport,
        val actor: String = UUID.randomUUID().toString(), val code: String? = null, private val now: () -> Long = System::currentTimeMillis) {
        private var seen: String? = null
        var mine = false
            private set
        suspend fun publish(state: Snapshot) {
            val frame = Frame(room, media, actor, state.position.coerceAtLeast(0), state.playing,
                now(), UUID.randomUUID().toString(), state.duration.coerceAtLeast(0), code)
            transport.write(frame)
            seen = frame.revision
            mine = true
        }
        suspend fun poll(state: Snapshot): Adjustment? {
            val frame = transport.read() ?: throw IllegalStateException("watch_unavailable")
            check(frame.room == room && frame.media == media) { "watch_mismatch" }
            check(abs(now() - frame.at) <= 120_000) { "watch_expired" }
            if (frame.writer == actor) { mine = true; return null }
            mine = false
            val fresh = seen != frame.revision
            seen = frame.revision
            val target = (frame.position + if (frame.playing) (now() - frame.at).coerceAtLeast(0) else 0)
                .coerceIn(0, state.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
            val seek = target.takeIf { abs(it - state.position) > 1_250 }
            return if (fresh || seek != null || state.playing != frame.playing) Adjustment(seek, frame.playing) else null
        }
    }
    /** File size and duration distinguish different edits/copies, not just a title. */
    fun identity(name: String, bytes: Long, seconds: Int): String = MessageDigest.getInstance("SHA-256")
        .digest("${name.trim().lowercase()}|$bytes|$seconds".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun encode(f: Frame): String = (listOf("TGSTREAM-WATCH-1", f.room, f.media, f.writer, f.position,
        if (f.playing) 1 else 0, f.at, f.revision, f.duration).map { it.toString() } + listOfNotNull(f.code)).joinToString("\n")
    fun decode(text: String?): Frame? {
        if (text == null || text.length > 1024) return null
        val p = text.split('\n')
        if (p.size !in 9..10 || p[0] != "TGSTREAM-WATCH-1" || !validRoom(p[1]) ||
            !p[2].matches(Regex("[a-f0-9]{64}")) || p[5] !in setOf("0", "1")) return null
        val pos = p[4].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val at = p[6].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val duration = p[8].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        if (p[3].length !in 1..64 || p[7].length !in 1..64) return null
        val code = p.getOrNull(9)
        if (code != null && !validCode(code)) return null
        return Frame(p[1], p[2], p[3], pos, p[5] == "1", at, p[7], duration, code)
    }
}
