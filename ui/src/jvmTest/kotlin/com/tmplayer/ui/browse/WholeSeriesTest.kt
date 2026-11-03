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
    @Test fun `Home includes small episodes and all later seasons`() = runTest {
        val chat = 81201L
        val opened = show(chat)
        val small = episode(chat, 3).copy(messageId = 203, fileId = 203, fileName = "Quiet Hills S02E01.mkv", title = "Quiet Hills S02E01.mkv", sizeBytes = 80L * 1024 * 1024)
        val whole = WholeSeries.read(opened, fetch = { _, _, _, _ -> MediaPage(listOf(small), MediaCursors(), true) }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(listOf(1, 2), whole.seasons.map { it.number })
        assertEquals(3, whole.episodeCount)
    }

    @Test fun `a slow complete search is not cut off at thirty seconds`() = runTest {
        val chat = 81202L
        var calls = 0
        val whole = WholeSeries.read(show(chat), fetch = { _, _, _, _ ->
            kotlinx.coroutines.delay(11_000)
            calls++
            MediaPage(listOf(episode(chat, calls + 2)), MediaCursors(video = calls.toLong()), calls == 3)
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(3, calls)
        assertEquals(5, whole.episodeCount)
    }

    @Test fun `detail search continues past the Home page budget to the actual end`() = runTest {
        val chat = 81203L
        var calls = 0
        val whole = WholeSeries.read(show(chat), fetch = { _, _, _, _ ->
            calls++
            MediaPage(listOf(episode(chat, calls + 2)), MediaCursors(video = calls.toLong()), calls == 10)
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(10, calls)
        assertEquals(12, whole.episodeCount)
    }

    @Test fun `same-count cached data cannot replace new episodes`() = runTest {
        val chat = 81204L
        WholeSeries.read(show(chat), fetch = { _, _, _, _ -> MediaPage(emptyList(), MediaCursors(), true) }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        val incoming = (SeriesShelf.arrange(listOf(episode(chat, 3), episode(chat, 4))).single() as ShelfEntry.Show).series
        assertEquals(4, WholeSeries.peek(incoming)?.episodeCount)
    }

    @Test fun `a Home show searches each known source chat`() = runTest {
        val a = 81205L
        val b = 81206L
        val opened = (SeriesShelf.arrange(listOf(episode(a, 1), episode(a, 2), episode(b, 3))).single() as ShelfEntry.Show).series
        val asked = mutableSetOf<Long>()
        val whole = WholeSeries.read(opened, fetch = { chat, _, _, _ ->
            asked += chat
            MediaPage(listOf(episode(chat, if (chat == a) 4 else 5)), MediaCursors(), true)
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(setOf(a, b), asked)
        assertEquals(5, whole.episodeCount)
    }

    private fun episode(chat: Long, number: Int) = MediaItem(
        chatId = chat, messageId = number.toLong(), fileId = number,
        title = "Quiet Hills S01E%02d.mkv".format(number),
        fileName = "Quiet Hills S01E%02d.mkv".format(number),
        sizeBytes = 900L * 1024 * 1024, durationSec = 2400,
        mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = number,
    )

    private fun show(chat: Long): Series =
        (SeriesShelf.arrange(listOf(episode(chat, 1), episode(chat, 2))).single() as ShelfEntry.Show).series

    @Test fun `first page appears before the next request finishes`() = runTest {
        val opened = show(81101)
        val counts = mutableListOf<Int>()
        var calls = 0
        val result = WholeSeries.read(opened, fetch = { _, _, _, _ ->
            calls++
            if (calls == 1) MediaPage(listOf(episode(81101, 3)), MediaCursors(video = 10), false)
            else {
                assertEquals(listOf(2, 3), counts)
                awaitCancellation()
            }
        }, onPage = { counts += it.episodeCount }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(3, result.episodeCount)
        assertEquals(3, WholeSeries.peek(opened)?.episodeCount)
        assertEquals(2, calls)
    }

    @Test fun `a failed later page retains episodes already received`() = runTest {
        val opened = show(81102)
        var calls = 0
        val result = WholeSeries.read(opened, fetch = { _, _, _, _ ->
            if (++calls == 1) MediaPage(listOf(episode(81102, 3)), MediaCursors(video = 10), false)
            else throw java.io.IOException("Offline")
        }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(3, result.episodeCount)
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
                if (++calls == 1) MediaPage(listOf(episode(81104, 3)), MediaCursors(video = 10), false)
                else throw CancellationException("Page closed")
            }, mergeDispatcher = StandardTestDispatcher(testScheduler))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(3, WholeSeries.peek(opened)?.episodeCount)
    }
}
