package com.tmplayer.ui.browse

import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaItem.Locality
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.HomeCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Home's Cached badges follow the watch cache after the rows have been fetched. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelCacheTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun video(id: Long, cached: Boolean) = MediaItem(
        chatId = 7, messageId = id, fileId = id.toInt(), title = "Video $id", sizeBytes = 600L * 1024 * 1024,
        durationSec = 60, mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = id.toInt(),
        onDevice = cached,
    )

    /** The one cached video, as the disk has it now. */
    private var onDisk = 1L

    private val source = object : HomeSource {
        override suspend fun chatMedia(chatId: Long) = listOf(video(1, cached = true), video(2, cached = false))
        override suspend fun recentMedia() = listOf(video(1, cached = true), video(2, cached = false))
        override suspend fun mediaItem(chatId: Long, messageId: Long) = video(messageId, cached = messageId == 1L)
        override suspend fun wholeOnDevice(item: MediaItem) = item.messageId == onDisk
    }

    @Test
    fun `cold storage does not block construction or overwrite fresh rows`() = runTest(dispatcher) {
        HomeCache.file = null
        HomeCache.clear()
        val disk = CompletableDeferred<HomeCache.Snapshot?>()
        var reading = false
        val model = HomeViewModel(source, disk = dispatcher, work = dispatcher, cached = true,
            cacheRead = { reading = true; disk.await() })
        assertFalse("No disk read in the constructor", reading)
        model.request(7)
        advanceUntilIdle()
        assertTrue(reading)
        val fresh = model.loaded.value.getValue(7)
        disk.complete(HomeCache.Snapshot(mapOf(7L to listOf(video(99, false))), null, setOf(7), emptyMap(), 0))
        advanceUntilIdle()
        assertEquals(fresh, model.loaded.value.getValue(7))
        assertFalse(7L in model.partial.value)
        HomeCache.clear()
    }

    @Test
    fun `late cache cannot restore a chat removed from the current selection`() = runTest(dispatcher) {
        HomeCache.file = null
        HomeCache.clear()
        val disk = CompletableDeferred<HomeCache.Snapshot?>()
        val model = HomeViewModel(source, disk = dispatcher, work = dispatcher, cached = true,
            cacheRead = { disk.await() })
        model.fill(emptyList())
        disk.complete(HomeCache.Snapshot(mapOf(7L to listOf(video(99, false))), null, emptySet(), emptyMap(), 0))
        advanceUntilIdle()
        assertTrue(model.loaded.value.isEmpty())
        HomeCache.clear()
    }

    @Test
    fun `playing another video moves the Cached badge to it`() = runTest(dispatcher) {
        // The disk is read on the test's dispatcher, so advancing it runs the recheck too.
        val model = HomeViewModel(source, disk = dispatcher)
        model.request(7)
        model.requestRecent()
        model.requestArt(
            ResumeRecord(
                chatId = 7, messageId = 1, fileId = 1, title = "Video 1", chatTitle = "Chat", sizeBytes = 1,
                durationSec = 60, positionMs = 1000, durationMs = 60_000, updatedAt = 0,
            ),
        )
        advanceUntilIdle()
        assertEquals(Locality.Cached, model.recent.value!!.first { it.messageId == 1L }.locality)

        // The second video played and the one-video cache let the first go.
        onDisk = 2
        model.refreshLocalAvailability()
        advanceUntilIdle()

        fun localities(items: List<MediaItem>) = items.associate { it.messageId to it.locality }
        val expected = mapOf(1L to Locality.Remote, 2L to Locality.Cached)
        assertEquals(expected, localities(model.recent.value!!))
        assertEquals(expected, localities(model.loaded.value.getValue(7)))
        assertEquals(Locality.Remote, model.art.value.values.single().locality)
    }
}
