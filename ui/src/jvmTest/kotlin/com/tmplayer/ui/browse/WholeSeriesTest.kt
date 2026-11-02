package com.tmplayer.ui.browse

import com.tmplayer.data.MediaCursors
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaPage
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.ShelfEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeSeriesTest {
    private fun episode(chat: Long, number: Int) = MediaItem(
        chatId = chat, messageId = number.toLong(), fileId = number,
        title = "Quiet Hills S01E%02d.mkv".format(number),
        fileName = "Quiet Hills S01E%02d.mkv".format(number),
        sizeBytes = 900L * 1024 * 1024, durationSec = 2400,
        mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = number,
    )

    private fun show(chat: Long): Series =
        (SeriesShelf.arrange(listOf(episode(chat, 1))).single() as ShelfEntry.Show).series

    @Test fun `first page appears before the next request finishes`() = runTest {
        val opened = show(81101)
        val counts = mutableListOf<Int>()
        var calls = 0
        val result = WholeSeries.read(opened, fetch = { _, _, _, _ ->
            calls++
            if (calls == 1) MediaPage(listOf(episode(81101, 2)), MediaCursors(video = 10), false)
            else {
                assertEquals(listOf(1, 2), counts)
                awaitCancellation()
            }
        }, onPage = { counts += it.episodeCount }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(2, result.episodeCount)
        assertEquals(2, WholeSeries.peek(opened)?.episodeCount)
        assertEquals(2, calls)
    }

    @Test fun `a failed later page retains episodes already received`() = runTest {
        val opened = show(81102)
        var calls = 0
        val result = WholeSeries.read(opened, fetch = { _, _, _, _ ->
            if (++calls == 1) MediaPage(listOf(episode(81102, 2)), MediaCursors(video = 10), false)
            else throw java.io.IOException("Offline")
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(2, result.episodeCount)
    }

    @Test fun `an unchanged cursor stops rather than repeating the search`() = runTest {
        var calls = 0
        WholeSeries.read(show(81103), fetch = { _, _, cursor, _ ->
            calls++
            MediaPage(emptyList(), cursor, false)
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(1, calls)
    }

    @Test fun `closing the page cancels without hiding the previous answer`() = runTest {
        val opened = show(81104)
        var calls = 0
        var cancelled = false
        try {
            WholeSeries.read(opened, fetch = { _, _, _, _ ->
                if (++calls == 1) MediaPage(listOf(episode(81104, 2)), MediaCursors(video = 10), false)
                else throw CancellationException("Page closed")
            }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(2, WholeSeries.peek(opened)?.episodeCount)
    }
}
