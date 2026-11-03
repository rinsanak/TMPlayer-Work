package com.tmplayer.player

import com.tmplayer.data.OpeningStall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamRecoveryTest {
    @Test fun `a brief network pause does not become a failure screen`() {
        assertFalse(OpeningStall.stalled(true, false, 0, 10_001, StreamRecovery.OPEN_STALL_MS))
        assertFalse(OpeningStall.stalled(true, false, 0, 45_001, StreamRecovery.REBUFFER_STALL_MS))
        assertTrue(OpeningStall.stalled(true, false, 0, 60_001, StreamRecovery.REBUFFER_STALL_MS))
        assertFalse(OpeningStall.stalled(true, true, 0, 100_000, StreamRecovery.OPEN_STALL_MS))
    }

    @Test fun `retry closes readers cancels every part then erases and resumes at the same point`() = runBlocking {
        val events = mutableListOf<String>()
        val at = StreamRecovery.resumeAt(731_234, 0)
        StreamRecovery.reset(listOf(10, 11, 10, 0), at,
            { events += "close" }, { events += "cancel:$it" }, { events += "erase:$it" }, { events += "resume:$it" })
        assertEquals(listOf("close", "cancel:10", "cancel:11", "erase:10", "erase:11", "resume:731234"), events)
    }

    @Test fun `a failed cache reset cannot reopen over half cleared bytes`() = runBlocking {
        var reopened = false
        try {
            StreamRecovery.reset(listOf(1, 2), 9000, {}, {}, { if (it == 2) throw java.io.IOException("busy") }, { reopened = true })
            fail("failed cleanup must stop the retry")
        } catch (_: java.io.IOException) { }
        assertFalse(reopened)
        assertEquals(9000L, StreamRecovery.resumeAt(0, 9000))
        assertEquals(listOf(7, 8), StreamRecovery.fileIds(7, listOf(0, 7, 8)))
    }
}
