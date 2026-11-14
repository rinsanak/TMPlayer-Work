package com.tmplayer.data

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DiagnosticUploadTest {
    @Test fun `an early acknowledgement is kept and unrelated sends are ignored`() = runTest {
        val outcomes = Channel<Pair<Long, Boolean>>(4)
        outcomes.send(9L to false)
        outcomes.send(42L to true)
        awaitDiagnosticConfirmation(42, outcomes)
        outcomes.close()
    }

    @Test fun `a matching failed send cannot be reported as success`() = runTest {
        val outcomes = Channel<Pair<Long, Boolean>>(1)
        outcomes.send(42L to false)
        try {
            awaitDiagnosticConfirmation(42, outcomes)
            fail("Failed upload must be retained for recovery")
        } catch (_: IOException) { } finally { outcomes.close() }
    }

    @Test fun `a queued message without acknowledgement stays unconfirmed`() = runTest {
        val outcomes = Channel<Pair<Long, Boolean>>(1)
        try {
            withTimeout(100) { awaitDiagnosticConfirmation(42, outcomes) }
            fail("Queue acceptance is not delivery")
        } catch (_: TimeoutCancellationException) { } finally { outcomes.close() }
    }
}
