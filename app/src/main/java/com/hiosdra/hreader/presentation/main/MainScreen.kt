package com.hiosdra.hreader.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.presentation.navigation.Routes
import com.hiosdra.hreader.presentation.article.ArticleImageDependencies
import com.hiosdra.hreader.presentation.article.ArticleListGrouped
import com.hiosdra.hreader.presentation.components.ArticleListSkeleton
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.presentation.theme.MotionDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    navController: NavController,
    onOpenSubscriptions: () -> Unit,
    onLeaveFeed: () -> Unit = {},
    onFeedMarkedRead: (Long) -> Unit = {},
    feedId: Long? = null,
    viewModel: MainViewModel,
    imageDependencies: ArticleImageDependencies
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val articles = viewModel.articles.collectAsLazyPagingItems()
    val refreshState = articles.loadState.refresh

    LaunchedEffect(feedId) { viewModel.setFeed(feedId) }

    val isSearching = uiState.searchQuery.isNotBlank()
    val unreadCount = if (isSearching) 0 else uiState.unreadCount
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberSaveable(feedId, saver = LazyListState.Saver) { LazyListState() }
    val searchActive = rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var offlineBannerDismissed by rememberSaveable { mutableStateOf(false) }
    val undoActionLabel = stringResource(R.string.action_undo)
    val retryActionLabel = stringResource(R.string.action_retry)
    val offlineStartedMessage = stringResource(R.string.offline_downloading)
    val emptyState = when {
        refreshState is LoadState.Error && articles.itemCount == 0 -> EmptyStateModel(
            error = stringResource(R.string.main_could_not_read_stored_articles),
            onRetry = articles::retry
        )
        articles.itemCount == 0 && uiState.syncState == SyncOperationState.FAILED -> EmptyStateModel(
            error = stringResource(R.string.error_refresh_articles),
            onRetry = viewModel::refreshFromNetwork
        )
        else -> EmptyStateModel(
            isSyncing = uiState.syncState == SyncOperationState.RUNNING,
            onRetry = viewModel::refreshFromNetwork
        )
    }

    BackHandler(enabled = searchActive.value) {
        searchActive.value = false
        viewModel.updateSearchQuery("")
        keyboardController?.hide()
    }

    LaunchedEffect(searchActive.value) {
        if (searchActive.value) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotBlank()) searchActive.value = true
    }

    LaunchedEffect(uiState.isOnline) {
        if (uiState.isOnline) offlineBannerDismissed = false
    }

    uiState.undo?.let { undo ->
        val undoMessage = undo.message.resolve()
        LaunchedEffect(undo.id) {
            try {
                snackbarHostState.showFeedback(
                    FeedbackRequest(
                        message = undoMessage,
                        kind = FeedbackKind.UNDO,
                        actionLabel = undoActionLabel,
                        onAction = viewModel::undoLastAction
                    )
                )
            } finally {
                viewModel.dismissUndo(undo.id)
            }
        }
    }

    uiState.error?.let { message ->
        val errorMessage = message.resolve()
        LaunchedEffect(message) {
            if (articles.itemCount == 0) return@LaunchedEffect
            snackbarHostState.showFeedback(
                FeedbackRequest(
                    message = errorMessage,
                    kind = FeedbackKind.RECOVERABLE_ERROR,
                    actionLabel = retryActionLabel,
                    onAction = viewModel::refreshFromNetwork
                )
            )
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MainTopBar(
                MainTopBarBindings(
                    uiState = uiState,
                    unreadCount = unreadCount,
                    feedId = feedId,
                    scrollBehavior = scrollBehavior,
                    searchActive = searchActive,
                    searchFocusRequester = searchFocusRequester,
                    keyboardController = keyboardController,
                    offlineBannerDismissed = offlineBannerDismissed,
                    onDismissOfflineBanner = { offlineBannerDismissed = true },
                    onOpenSubscriptions = onOpenSubscriptions,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onDismissAiModelWarning = viewModel::dismissAiModelWarning,
                    onRefresh = viewModel::refreshFromNetwork,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onShowReadArticles = viewModel::setShowReadArticles,
                    onPrepareOffline = viewModel::prepareForOffline,
                    onLeaveFeed = onLeaveFeed,
                    snackbarHostState = snackbarHostState,
                    snackbarScope = snackbarScope,
                    offlineStartedMessage = offlineStartedMessage
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = unreadCount > 0,
                enter = fadeIn(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                ),
                exit = fadeOut(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                )
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!uiState.isBulkReadStateUpdating) {
                            viewModel.markAllAsRead(onFeedMarkedRead)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    text = {
                        Text(
                            text = pluralStringResource(
                                R.plurals.main_mark_articles_read,
                                unreadCount,
                                unreadCount
                            )
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            refreshState is LoadState.Loading && articles.itemCount == 0 ->
                ArticleListSkeleton(modifier = Modifier.padding(paddingValues))

            articles.itemCount == 0 &&
                !uiState.hasCompletedSync &&
                uiState.readCount == 0 &&
                uiState.syncState == SyncOperationState.RUNNING ->
                InitialSyncState(modifier = Modifier.padding(paddingValues))

            articles.itemCount == 0 -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                model = emptyState,
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                showReadArticles = uiState.showReadArticles,
                readCount = uiState.readCount,
                feedId = feedId,
                onClearSearch = { viewModel.updateSearchQuery("") },
                onBrowseFeeds = onOpenSubscriptions,
                onAddFeed = { navController.navigate(Routes.addFeed()) },
                onBack = onLeaveFeed,
                onShowAllArticles = {
                    viewModel.setShowReadArticles(true)
                    viewModel.updateSearchQuery("")
                }
            )

            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    if (uiState.isOnline) viewModel.refreshFromNetwork()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ArticleListGrouped(
                    items = articles,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    onOpen = { articleId ->
                        val query = viewModel.currentQuery()
                        navController.navigate(
                            Routes.article(
                                feedId = query.feedId,
                                startArticleId = articleId,
                                includeRead = query.includeRead,
                                sessionStartMillis = query.sessionStart.toEpochMilli()
                            )
                        )
                    },
                    onCheckedChange = viewModel::updateEntryReadStatus,
                    imageDependencies = imageDependencies,
                    readStateAnimationEnabled = !uiState.isBulkReadStateUpdating &&
                        uiState.syncState != com.hiosdra.hreader.core.application.sync.SyncOperationState.RUNNING,
                    isOnline = uiState.isOnline
                )
            }
        }
    }
}
