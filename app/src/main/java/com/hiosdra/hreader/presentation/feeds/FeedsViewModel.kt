package com.hiosdra.hreader.presentation.feeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.usecase.feeds.FeedUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.presentation.text.UiText
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
    val error: UiText? = null,
    /** Result of the last delete, rename or import, for a snackbar rather than a dialog. */
    val message: UiText? = null,
    val messageIsError: Boolean = false,
    val messageCanRetry: Boolean = false,
    val isBusy: Boolean = false,
    val isOnline: Boolean = true
)

class FeedsViewModel(
    private val feeds: FeedUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedsUiState(isOnline = feeds.isOnline.value)
    )
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    /**
     * Where each feed sits, settled when the panel opens and held until it opens again. Counts keep
     * arriving after the list is on screen — from the cache first, then the server — and reordering
     * on each of them would move the rows under the reader's finger.
     */
    private var rowOrder: Map<Long, Int> = emptyMap()
    private var resettleRows = true
    private var retryAction: (() -> Unit)? = null

    init {
        loadFeeds()
        viewModelScope.launch {
            feeds.isOnline.drop(1).collect { online ->
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
            val cachedFeeds = runCatchingCancellable { feeds.getCachedFeeds() }.getOrDefault(emptyList())
            val cachedCounts = runCatchingCancellable { feeds.getCachedUnreadCounts() }.getOrDefault(emptyMap())
            if (cachedFeeds.isNotEmpty()) {
                publish(cachedFeeds, cachedCounts)
            }

            if (!feeds.isOnline.value) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val refreshed = runCatchingCancellable { feeds.refreshFeeds() }
            refreshed.fold(
                onSuccess = { freshFeeds ->
                    val counts = runCatchingCancellable { feeds.getUnreadCounts() }.getOrDefault(cachedCounts)
                    publish(freshFeeds, counts)
                },
                onFailure = { failure ->
                    Log.w("FeedsViewModel", "Falling back to the cached subscriptions", failure)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = UiText.Resource(R.string.feeds_load_error).takeIf { cachedFeeds.isEmpty() }
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
        val title = _uiState.value.feeds.find { it.id == feedId }?.title
        runFeedAction(
            success = {
                if (title == null) {
                    UiText.Resource(R.string.feeds_unsubscribed_generic)
                } else {
                    UiText.Resource(R.string.feeds_unsubscribed, listOf(title))
                }
            },
            failure = { _ -> UiText.Resource(R.string.feeds_unsubscribe_error) },
            retry = { deleteFeed(feedId) }
        ) { feeds.deleteFeed(feedId) }
    }

    fun renameFeed(feedId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        runFeedAction(
            failure = { _ -> UiText.Resource(R.string.feeds_rename_error) },
            retry = { renameFeed(feedId, trimmed) }
        ) { feeds.renameFeed(feedId, trimmed) }
    }

    fun setAiOverviewPreloading(feedId: Long, enabled: Boolean) {
        if (_uiState.value.isBusy) return
        val current = _uiState.value.feeds.firstOrNull { it.id == feedId } ?: return
        if (current.preloadAiOverview == enabled) return

        updateAiOverviewPreloading(feedId, enabled)
        retryAction = null
        _uiState.value = _uiState.value.copy(isBusy = true, message = null)
        viewModelScope.launch {
            val result = runCatchingCancellable {
                feeds.setAiOverviewPreloading(feedId, enabled)
            }
            val failed = result.isFailure
            if (failed) updateAiOverviewPreloading(feedId, current.preloadAiOverview)
            retryAction = { setAiOverviewPreloading(feedId, enabled) }.takeIf { failed }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = UiText.Resource(R.string.feeds_preload_ai_overview_error).takeIf { failed },
                messageIsError = failed,
                messageCanRetry = failed
            )
        }
    }

    fun importOpml(xml: String) {
        runFeedAction(
            success = {
                UiText.Resource(
                    R.string.feeds_imported,
                    listOf(
                        UiText.Plural(R.plurals.feeds_imported_added, it.added, listOf(it.added)),
                        UiText.Plural(
                            R.plurals.feeds_imported_subscribed,
                            it.skipped,
                            listOf(it.skipped)
                        ),
                        UiText.Plural(
                            R.plurals.feeds_imported_failed,
                            it.failed.size,
                            listOf(it.failed.size)
                        )
                    )
                )
            },
            failure = { _ -> UiText.Resource(R.string.feeds_import_error) }
        ) { feeds.importOpml(xml) }
    }

    /**
     * [write] receives the OPML and reports whether it landed. The panel owns the file handle the
     * storage picker returned; the view model owns what to say about the outcome.
     */
    suspend fun exportOpmlTo(title: String, write: suspend (String) -> Boolean) {
        retryAction = null
        val opml = runCatchingCancellable { feeds.exportOpml(title) }
            .onFailure { Log.w("FeedsViewModel", "OPML export failed", it) }
            .getOrNull()
        val written = opml != null && write(opml)
        _uiState.value = _uiState.value.copy(
            message = UiText.Resource(if (written) R.string.feeds_exported else R.string.feeds_file_write_failed),
            messageIsError = !written,
            messageCanRetry = false
        )
    }

    fun reportUnreadableFile() {
        retryAction = null
        _uiState.value = _uiState.value.copy(
            message = UiText.Resource(R.string.feeds_file_read_failed),
            messageIsError = true,
            messageCanRetry = false
        )
    }

    fun dismissMessage() {
        retryAction = null
        _uiState.value = _uiState.value.copy(
            message = null,
            messageIsError = false,
            messageCanRetry = false
        )
    }

    fun retryLastAction() {
        val retry = retryAction ?: return
        retryAction = null
        _uiState.value = _uiState.value.copy(
            message = null,
            messageIsError = false,
            messageCanRetry = false
        )
        retry()
    }

    fun nextFeedId(currentFeedId: Long): Long? {
        val state = _uiState.value
        val visibleFeeds = state.filteredFeeds
        return if (visibleFeeds.any { it.id == currentFeedId }) {
            nextSubscriptionId(visibleFeeds, currentFeedId)
        } else {
            nextSubscriptionId(state.feeds, currentFeedId)
        }
    }

    private fun <T> runFeedAction(
        success: ((T) -> UiText)? = null,
        failure: (Throwable) -> UiText,
        retry: (() -> Unit)? = null,
        action: suspend () -> T
    ) {
        if (!feeds.isOnline.value) {
            retryAction = null
            _uiState.value = _uiState.value.copy(
                message = UiText.Resource(R.string.feeds_need_connection),
                messageIsError = true,
                messageCanRetry = false
            )
            return
        }
        viewModelScope.launch {
            retryAction = null
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                message = null,
                messageIsError = false,
                messageCanRetry = false
            )
            val result = runCatchingCancellable { action() }
            val failed = result.isFailure
            retryAction = retry.takeIf { failed }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = feedActionUiText(result, success, failure),
                messageIsError = failed,
                messageCanRetry = failed && retry != null
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

    private fun updateAiOverviewPreloading(feedId: Long, enabled: Boolean) {
        val updatedFeeds = _uiState.value.feeds.map { feed ->
            if (feed.id == feedId) feed.copy(preloadAiOverview = enabled) else feed
        }
        val updatedFilteredFeeds = _uiState.value.filteredFeeds.map { feed ->
            if (feed.id == feedId) feed.copy(preloadAiOverview = enabled) else feed
        }
        _uiState.value = _uiState.value.copy(
            feeds = updatedFeeds,
            filteredFeeds = updatedFilteredFeeds
        )
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

private fun <T> feedActionUiText(
    result: Result<T>,
    success: ((T) -> UiText)?,
    failure: (Throwable) -> UiText
): UiText? = result.fold(
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

internal fun nextSubscriptionId(feeds: List<Feed>, currentFeedId: Long): Long? {
    val currentIndex = feeds.indexOfFirst { it.id == currentFeedId }
    if (currentIndex == -1) return null
    return feeds.getOrNull(currentIndex + 1)?.id
}
