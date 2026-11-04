package com.tmplayer.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DialogBoostStoreTest {
    @Test fun `boost setting survives new wrapper and Home removal preserves saved position`() = runBlocking {
        val dir = Files.createTempDirectory("dialog-boost").toFile()
        val data = SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME))
        val store = SettingsStore(data)
        assertFalse(store.dialogBoostNow())
        store.saveResumePosition(-100, 1, 400_000, 3_000_000, ResumeRecord.encode(fileId = 1,
            title = "Quiet Hills S01E01.mkv", chatTitle = "Show", sizeBytes = 900_000_000, durationSec = 3000, updatedAt = 1000))
        val records = store.continueWatching.first()
        store.setDialogBoost(true)
        assertTrue(SettingsStore(data).dialogBoostNow())
        val before = TgStreamUi.active
        try {
            TgStreamUi.active = true
            assertFalse(store.homeLayout.first().continueWatching)
            val rows = HomeRows.build(records, emptyList(), emptyMap(), emptyList())
            assertTrue(rows.none { it is HomeRow.Continue })
            assertTrue(HomeContent.clean(listOf(HomeRow.Continue(records))).none { it is HomeRow.Continue })
            assertEquals(records, store.continueWatching.first())
            assertEquals(400_000L, records.single().positionMs)
        } finally { TgStreamUi.active = before }
        store.setDialogBoost(false)
        assertFalse(SettingsStore(data).dialogBoostNow())
    }
}
