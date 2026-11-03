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
        try {
            DiagnosticSession.install(RuntimeEnvironment.getApplication())
            val first = DiagnosticSession.start()
            assertEquals(first, DiagnosticSession.start())
            repeat(20) { DiagnosticSession.event("CHECK", "step=$it") }
            val saved = DiagnosticSession.stop()
            val lines = saved.readText()
            assertTrue(lines.contains("step=19")); assertTrue(lines.contains("SESSION_STOP"))
            assertEquals(saved, DiagnosticSession.stop())
            assertEquals(first + 1, DiagnosticSession.start())
            assertFalse(DiagnosticSession.stop().readText().contains("step=19"))
            assertFalse(DiagnosticSession.status.value.recording)
        } finally { runCatching { DiagnosticSession.stop() }; Thread.setDefaultUncaughtExceptionHandler(previous) }
    }
}
