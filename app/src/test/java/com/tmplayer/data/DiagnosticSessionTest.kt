package com.tmplayer.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DiagnosticSessionTest {
    private fun content(file: java.io.File): String = java.util.zip.ZipFile(file).use { zip ->
        zip.entries().asSequence().filter { it.name.startsWith("part-") }.sortedBy { it.name }
            .joinToString("\n") { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
    }
    @Test fun `start is idempotent stop flushes accepted events and serials survive sessions`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val field = DiagnosticSession::class.java.getDeclaredField("worker").apply { isAccessible = true }
        val worker = field.get(null) as java.util.concurrent.ThreadPoolExecutor
        worker.setKeepAliveTime(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
            DiagnosticSession.install(RuntimeEnvironment.getApplication())
            DiagnosticSession.phase("COLD_START", "cache read in 125 ms")
            val first = DiagnosticSession.start()
            assertEquals(first, DiagnosticSession.start())
            repeat(20) { DiagnosticSession.event("CHECK", "step=$it") }
            val deadline = System.currentTimeMillis() + 2000
            while (worker.poolSize != 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            assertEquals("Stop must also drain a recorder whose idle writer thread has exited", 0, worker.poolSize)
            val saved = DiagnosticSession.stop()
            assertEquals("zip", saved.extension)
            val lines = content(saved)
            assertTrue(lines.contains("step=19")); assertTrue(lines.contains("SESSION_STOP"))
            assertTrue(lines.contains("STARTUP_TRAIL") && lines.contains("COLD_START"))
            val originalBytes = saved.readBytes()
            assertEquals(saved, DiagnosticSession.stop())
            assertArrayEquals(originalBytes, saved.readBytes())
            assertEquals(first + 1, DiagnosticSession.start())
            assertFalse(content(DiagnosticSession.stop()).contains("step=19"))
            assertFalse(DiagnosticSession.status.value.recording)
        } finally { runCatching { DiagnosticSession.stop() }; Thread.setDefaultUncaughtExceptionHandler(previous); worker.setKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Test fun `TV ZIP preserves early phases and excludes private diagnostic strings`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        FormFactor.override(true)
        try {
            DiagnosticSession.install(RuntimeEnvironment.getApplication())
            DiagnosticSession.phase("APP_TEST", "early launch")
            repeat(80) { DiagnosticSession.phase("NOISE", "sample=$it") }
            DiagnosticSession.start()
            assertTrue(DiagnosticSession.status.value.pendingExport)
            DiagnosticSession.coreLog("Td", "auth: Waiting -> Ready")
            DiagnosticSession.coreLog("Td", "private message text")
            DiagnosticSession.coreWarning("Td", "private message text", IllegalStateException("private message text"))
            DiagnosticSession.event("HOME_CARD", "rawName=private.mkv fileId=123")
            DiagnosticSession.event("FIRST_FRAME", "sincePlayMs=1234")

            val lines = content(DiagnosticSession.stop())
            assertTrue(lines.contains("APP_TEST early launch"))
            assertTrue(lines.contains("Td auth: Waiting -> Ready"))
            assertTrue(lines.contains("CORE_WARNING") && lines.contains("IllegalStateException"))
            assertTrue(lines.contains("FIRST_FRAME atElapsedMs=") && lines.contains("sincePlayMs=1234"))
            assertFalse(lines.contains("private message text"))
            assertFalse(lines.contains("private.mkv"))
            assertFalse(lines.contains(" LOGCAT "))
            assertFalse(DiagnosticSession.status.value.pendingExport)
        } finally {
            runCatching { DiagnosticSession.stop() }
            FormFactor.override(false)
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

}
