package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagnosticJournalTest {
    @Test fun `concurrent records preserve sequence and every record across rotation`() {
        val root = Files.createTempDirectory("diagnostic-test").toFile()
        try {
            val log = DiagnosticJournal(root, 7, partBytes = 300)
            val threads = (1..8).map { n -> Thread { repeat(20) { log.record("WORKER", "$n:$it") } }.apply { start() } }
            threads.forEach { it.join() }; log.close()
            val lines = log.files.flatMap { it.readLines() }
            assertEquals(160, lines.size)
            assertEquals((1..160).toList(), lines.map { it.substringAfter('#').substringBefore(' ').toInt() })
            assertTrue(log.files.size > 1)
            log.record("LATE", "closed")
            assertEquals(160, log.files.sumOf { it.readLines().size })
        } finally { root.deleteRecursively() }
    }
    @Test fun `credentials are removed without losing numeric playback and memory counters`() {
        val text = DiagnosticRedactor.scrub("positionMs=731234 heap=128000000 api_key=sekret Authorization: Bearer hidden https://server/file?token=hidden phone_number=966555555555")
        assertTrue(text, "731234" in text && "128000000" in text)
        assertFalse(text, "sekret" in text || "hidden" in text || "966555555555" in text || "https://" in text)
        assertFalse(DiagnosticRedactor.scrub("{\"api_hash\":\"supersecret\"}").contains("supersecret"))
    }
    @Test fun `a file cannot be used as a directory and existing data is preserved`() {
        val root = Files.createTempFile("diagnostic-file", ".txt").toFile()
        try {
            root.writeText("preserve")
            try { DiagnosticJournal(root, 1); fail("must reject a file as directory") } catch (_: java.io.IOException) { }
            assertEquals("preserve", root.readText())
        } finally { root.delete() }
    }
}
