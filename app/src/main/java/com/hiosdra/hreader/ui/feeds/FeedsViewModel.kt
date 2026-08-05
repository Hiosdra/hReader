package com.hiosdra.hreader.ui.feeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.repository.FeedRepository
import com.hiosdra.hreader.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class FeedsUiState(
    val feeds: List<Feed> = emptyList(),
    val filteredFeeds: List<Feed> = emptyList(),
    val searchQuery: String = "",
    val unreadCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Result of the last delete, rename or import, for a snackbar rather than a dialog. */
    val message: String? = null,
    val isBusy: Boolean = false,
    val isOnline: Boolean = true
)

class FeedsViewModel(
    private val feedRepository: FeedRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedsUiState(isOnline = networkMonitor.isOnline.value)
    )
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    /**
     * Where each feed sits, settled when the panel opens and held until it opens again. Counts keep
     * arriving after the list is on screen — from the cache first, then the server — and reordering
     * on each of them would move the rows under the reader's finger.
     */
    private var rowOrder: Map<Long, Int> = emptyMap()
    private var resettleRows = true

    init {
        loadFeeds()
        viewModelScope.launch {
            networkMonitor.isOnline.drop(1).collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
                if (online) {
                    resettleRows = true
                    loadFeeds()
                }
            }
        }
    }

    fun reload() {
        resettleRows = true
        loadFeeds()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterFeeds()
    }

    private fun filterFeeds() {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val filteredFeeds = if (query.isEmpty()) {
            _uiState.value.feeds
        } else {
            _uiState.value.feeds.filter { feed ->
                feed.title.lowercase().contains(query) ||
                feed.siteUrl?.lowercase()?.contains(query) == true
            }
        }
        _uiState.value = _uiState.value.copy(filteredFeeds = filteredFeeds)
    }

    /**
     * The cache first, the server second. Subscriptions change rarely and the local copy is
     * complete, so a failed refresh is only an error when there is nothing cached to show.
     */
    private fun loadFeeds() {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val cachedFeeds = runCatching { feedRepository.getCachedFeeds() }.getOrDefault(emptyList())
            val cachedCounts = runCatching { feedRepository.getCachedUnreadCounts() }.getOrDefault(emptyMap())
            if (cachedFeeds.isNotEmpty()) {
                publish(cachedFeeds, cachedCounts)
            }

            if (!networkMonitor.isOnline.value) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val refreshed = runCatching { feedRepository.refreshFeeds() }
            refreshed.fold(
                onSuccess = { feeds ->
                    val counts = runCatching { feedRepository.getUnreadCounts() }.getOrDefault(cachedCounts)
                    publish(feeds, counts)
                },
                onFailure = { failure ->
                    Log.w("FeedsViewModel", "Falling back to the cached subscriptions", failure)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error loading feeds: ${failure.message}".takeIf { cachedFeeds.isEmpty() }
                    )
                }
            )
        }
    }

    /**
     * The server decides first. Removing the feed locally and letting the sync catch up would show
     * it gone and then bring it back, articles and all, the next time the app synced.
     */
    fun deleteFeed(feedId: Long) {
        val title = _uiState.value.feeds.find { it.id == feedId }?.title ?: "Feed"
        runFeedAction(
            success = { "Unsubscribed from $title" },
            failure = { "Could not unsubscribe: ${it.message}" }
        ) { feedRepository.deleteFeed(feedId) }
    }

    fun renameFeed(feedId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        runFeedAction(
            failure = { "Could not rename: ${it.message}" }
        ) { feedRepository.renameFeed(feedId, trimmed) }
    }

    fun importOpml(xml: String) {
        runFeedAction(
            success = { "Imported: ${it.added} added, ${it.skipped} already subscribed, ${it.failed.size} failed" },
            failure = { "Import failed: ${it.message}" }
        ) { feedRepository.importOpml(xml) }
    }

    /**
     * [write] receives the OPML and reports whether it landed. The panel owns the file handle the
     * storage picker returned; the view model owns what to say about the outcome.
     */
    suspend fun exportOpmlTo(write: suspend (String) -> Boolean) {
        val opml = runCatching { feedRepository.exportOpml() }
            .onFailure { Log.w("FeedsViewModel", "OPML export failed", it) }
            .getOrNull()
        val written = opml != null && write(opml)
        _uiState.value = _uiState.value.copy(
            message = if (written) "Subscriptions exported" else "Could not write that file"
        )
    }

    fun reportUnreadableFile() {
        _uiState.value = _uiState.value.copy(message = "Could not read that file")
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun <T> runFeedAction(
        success: ((T) -> String)? = null,
        failure: (Throwable) -> String,
        action: suspend () -> T
    ) {
        if (!networkMonitor.isOnline.value) {
            _uiState.value = _uiState.value.copy(message = "This action needs a connection")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            val result = runCatching { action() }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = feedActionMessage(result, success, failure)
            )
            if (result.isSuccess) loadFeeds()
        }
    }

    private fun placeRows(feeds: List<Feed>, unreadCounts: Map<Long, Int>): List<Feed> {
        val placed = if (resettleRows) {
            sortSubscriptions(feeds, unreadCounts)
        } else {
            holdRowOrder(feeds, unreadCounts, rowOrder)
        }
        rowOrder = placed.withIndex().associate { (position, feed) -> feed.id to position }
        resettleRows = false
        return placed
    }

    private fun publish(feeds: List<Feed>, unreadCounts: Map<Long, Int>) {
        val ordered = placeRows(feeds, unreadCounts)
        _uiState.value = _uiState.value.copy(
            feeds = ordered,
            filteredFeeds = ordered,
            unreadCounts = unreadCounts,
            isLoading = false,
            error = null
        )
        if (_uiState.value.searchQuery.isNotEmpty()) {
            filterFeeds()
        }
    }
}

internal fun <T> feedActionMessage(
    result: Result<T>,
    success: ((T) -> String)?,
    failure: (Throwable) -> String
): String? = result.fold(
    onSuccess = { success?.invoke(it) },
    onFailure = failure
)

/** The feeds with something to read come first, the rest alphabetically. */
internal fun sortSubscriptions(feeds: List<Feed>, unreadCounts: Map<Long, Int>): List<Feed> =
    feeds.sortedWith(
        compareByDescending<Feed> { unreadCounts[it.id] ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )

/**
 * The positions [rowOrder] already holds. Feeds it has never placed — a fresh subscription, an OPML
 * import — go after them, among themselves in the order [sortSubscriptions] would give.
 */
internal fun holdRowOrder(
    feeds: List<Feed>,
    unreadCounts: Map<Long, Int>,
    rowOrder: Map<Long, Int>
): List<Feed> = sortSubscriptions(feeds, unreadCounts).sortedBy { rowOrder[it.id] ?: Int.MAX_VALUE }
