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
    @Test fun `start is idempotent stop flushes accepted events and serials survive sessions`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val field = DiagnosticSession::class.java.getDeclaredField("worker").apply { isAccessible = true }
        val worker = field.get(null) as java.util.concurrent.ThreadPoolExecutor
        worker.setKeepAliveTime(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
            DiagnosticSession.install(RuntimeEnvironment.getApplication())
            val first = DiagnosticSession.start()
            assertEquals(first, DiagnosticSession.start())
            repeat(20) { DiagnosticSession.event("CHECK", "step=$it") }
            val deadline = System.currentTimeMillis() + 2000
            while (worker.poolSize != 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            assertEquals("Stop must also drain a recorder whose idle writer thread has exited", 0, worker.poolSize)
            val saved = DiagnosticSession.stop()
            val lines = saved.readText()
            assertTrue(lines.contains("step=19")); assertTrue(lines.contains("SESSION_STOP"))
            assertEquals(saved, DiagnosticSession.stop())
            assertEquals(first + 1, DiagnosticSession.start())
            assertFalse(DiagnosticSession.stop().readText().contains("step=19"))
            assertFalse(DiagnosticSession.status.value.recording)
        } finally { runCatching { DiagnosticSession.stop() }; Thread.setDefaultUncaughtExceptionHandler(previous); worker.setKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS) }
    }
}
