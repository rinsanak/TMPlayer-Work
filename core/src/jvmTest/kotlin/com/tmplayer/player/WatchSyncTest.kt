package com.tmplayer.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WatchSyncTest {
    private val room = "1".repeat(32)
    private val media = WatchSync.identity("Show S01E01.mkv", 900_000_000, 2400)
    private class Relay : WatchSync.Transport {
        var frame: WatchSync.Frame? = null
        override suspend fun read() = frame
        override suspend fun write(frame: WatchSync.Frame) { this.frame = WatchSync.decode(WatchSync.encode(frame)) }
    }
    @Test fun `short numeric aliases retain distinct full session identity and legacy compatibility`() = runTest {
        repeat(20) { val code = WatchSync.newCode(); assertEquals(4, code.length); assertTrue(WatchSync.validCode(code)) }
        assertFalse(WatchSync.validCode("12"))
        val legacy = WatchSync.Frame(room, media, "a", 20, false, 1_000_000, "revision", 100_000)
        assertEquals(legacy, WatchSync.decode(WatchSync.encode(legacy)))
        val numeric = legacy.copy(code = "1234")
        assertEquals(numeric, WatchSync.decode(WatchSync.encode(numeric)))
        assertTrue(WatchSync.active(numeric, 1_000_000))
        assertFalse(WatchSync.active(numeric, 1_121_000))
        val relay = Relay()
        val client = WatchSync.Client(room, media, relay, "device", code = "1234") { 1_000_000L }
        client.publish(WatchSync.Snapshot(20, false, 100_000))
        assertEquals("1234", relay.frame?.code)
        relay.frame = relay.frame?.copy(room = "2".repeat(32))
        assertEquals("watch_mismatch", runCatching { client.poll(WatchSync.Snapshot(20, false, 100_000)) }.exceptionOrNull()?.message)
    }

    @Test fun `two independent clients sync play pause seek and correct reconnect drift`() = runTest {
        var time = 1_000_000L
        val relay = Relay()
        val a = WatchSync.Client(room, media, relay, "device-a") { time }
        val b = WatchSync.Client(room, media, relay, "device-b") { time }
        a.publish(WatchSync.Snapshot(40_000, true, 100_000))
        time += 2000
        val start = b.poll(WatchSync.Snapshot(0, false, 100_000))!!
        assertEquals(42_000L, start.position); assertTrue(start.playing)
        b.publish(WatchSync.Snapshot(43_000, false, 100_000))
        val pause = a.poll(WatchSync.Snapshot(44_000, true, 100_000))!!
        assertFalse(pause.playing)
        b.publish(WatchSync.Snapshot(80_000, true, 100_000))
        assertEquals(80_000L, a.poll(WatchSync.Snapshot(43_000, false, 100_000))?.position)
        time += 10_000
        assertEquals(90_000L, a.poll(WatchSync.Snapshot(81_000, true, 100_000))?.position)
        assertNull(a.poll(WatchSync.Snapshot(90_000, true, 100_000)))
    }
    @Test fun `different copy and expired sessions cannot move a player`() = runTest {
        val relay = Relay()
        var now = 1_000_000L
        WatchSync.Client(room, media, relay, "a") { now }.publish(WatchSync.Snapshot(1, true, 100_000))
        val wrong = WatchSync.Client(room, WatchSync.identity("Show S01E02.mkv", 900_000_000, 2400), relay, "b") { now }
        assertEquals("watch_mismatch", runCatching { wrong.poll(WatchSync.Snapshot(0, false, 100_000)) }.exceptionOrNull()?.message)
        now += 121_000
        val late = WatchSync.Client(room, media, relay, "b") { now }
        assertEquals("watch_expired", runCatching { late.poll(WatchSync.Snapshot(0, false, 100_000)) }.exceptionOrNull()?.message)
        assertNotEquals(media, WatchSync.identity("Show S01E01.mkv", 500_000_000, 2400))
    }
    @Test fun `malformed control data is rejected`() {
        for (s in listOf("", "TGSTREAM-WATCH-1", "x".repeat(2000), "TGSTREAM-WATCH-1\n" + room)) assertNull(WatchSync.decode(s))
    }
}
