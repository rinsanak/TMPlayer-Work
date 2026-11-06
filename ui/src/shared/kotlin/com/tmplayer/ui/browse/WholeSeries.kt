package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.tmplayer.data.ChatRepository
import com.tmplayer.data.MediaCursors
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaPage
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesFill
import com.tmplayer.data.Td
import com.tmplayer.platform.LruMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A show's page with every episode its chat holds, not only those the screen it was opened from
 * had read ([SeriesFill]): [series] on the first frame, then the show made whole once its chat has
 * been searched for it, a page at a time up to [SeriesFill.MAX_PAGES]. Kept per show for the run,
 * so opening it again draws it whole at once and asks again only behind it.
 */
@Composable
fun rememberWholeSeries(series: Series): Series {
    val initial = remember(series) { WholeSeries.peek(series) ?: series }
    val whole by produceState(initial, series) {
        val read = runCatching { WholeSeries.read(series, onPage = { value = it }) }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            null
        }
        if (read != null && read != value) value = read
    }
    return whole
}

/** The searches behind [rememberWholeSeries], and what they found, by show. */
object WholeSeries {
    private val kept = LruMap<String, Series>(KEPT)

    /** [series] as made whole before, when that still holds every episode it has now. */
    fun peek(series: Series): Series? =
        kept[keyOf(series)]?.let { SeriesFill.combine(series, it) }

    /**
     * Reads this title to the end in each known source chat/topic. Discovery size limits do
     * not apply to a show's episodes. A page timeout, repeated cursor or screen close stops it.
     */
    suspend fun read(
        series: Series,
        fetch: suspend (chatId: Long, query: String, cursors: MediaCursors, topicId: Int) -> MediaPage? = ::telegram,
        onPage: (Series) -> Unit = {},
        mergeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Series {
        val query = SeriesFill.queryOf(series) ?: return series
        var whole = peek(series) ?: series
        onPage(whole)
        val sources = whole.episodes.flatMap { it.copies }.map { it.chatId to 0 }.distinct()

        // Draw each answer as it arrives. Slow later pages must not hide usable episodes,
        // and closing the page cancels the read while retaining the answers already drawn.
        for ((chatId, topic) in sources) {
            var cursors = MediaCursors()
            val visited = HashSet<MediaCursors>()
            // A detail page reads to the end of this title, not Home's discovery budget.
            while (visited.add(cursors)) {
                val read = try {
                    withTimeoutOrNull(PAGE_TIMEOUT_MS) { fetch(chatId, query, cursors, topic) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    null
                } ?: break
                val next = withContext(mergeDispatcher) {
                    // Known series membership, not a discovery size slider, decides episodes.
                    SeriesFill.merge(whole, read.items)
                }
                if (next != whole) {
                    whole = next
                    kept[keyOf(series)] = whole
                    onPage(whole)
                }
                if (read.endReached || read.cursors.allDone) break
                cursors = read.cursors
            }
        }
        kept[keyOf(series)] = whole
        com.tmplayer.online.KnownMedia.note(whole.episodes.flatMap { it.copies })
        return whole
    }

    private suspend fun telegram(chatId: Long, query: String, cursors: MediaCursors, topicId: Int): MediaPage? {
        val session = Td.awaitConnectedSessionOrNull() ?: return null
        return ChatRepository(session.client).mediaPage(chatId, cursors, query = query, pageSize = SeriesFill.PAGE, topicId = topicId)
    }

    private fun keyOf(series: Series) = "${series.cover.chatId}|${series.key}"

    /** Shows kept made whole: a run's worth of pages opened. */
    private const val KEPT = 48
    private const val PAGE_TIMEOUT_MS = 12_000L
}
