package com.hiosdra.hreader.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.navigation.Routes
import com.hiosdra.hreader.presentation.article.ArticleListGrouped
import com.hiosdra.hreader.presentation.components.ArticleListSkeleton
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.presentation.theme.MotionDuration
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    onOpenSubscriptions: () -> Unit,
    onLeaveFeed: () -> Unit = {},
    onFeedMarkedRead: (Long) -> Unit = {},
    feedId: Long? = null,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val articles = viewModel.articles.collectAsLazyPagingItems()

    LaunchedEffect(feedId) { viewModel.setFeed(feedId) }

    // The counter and "mark all read" are about the whole list, which a search is not showing.
    // Left visible they offered to mark hundreds of articles the reader could not see.
    val isSearching = uiState.searchQuery.isNotBlank()
    val unreadCount = if (isSearching) 0 else uiState.unreadCount
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberSaveable(feedId, saver = LazyListState.Saver) { LazyListState() }
    val searchActive = rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val undoActionLabel = stringResource(R.string.action_undo)
    val retryActionLabel = stringResource(R.string.action_retry)
    val offlineRefreshMessage = stringResource(R.string.main_offline_refresh)

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

    // Actions that already happened are offered back, instead of being asked about beforehand.
    uiState.undo?.let { undo ->
        val undoMessage = undo.message.resolve()
        LaunchedEffect(undo.id) {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = undoMessage,
                    actionLabel = undoActionLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoLastAction()
            } finally {
                // Also when this is cancelled. Opening an article takes the snackbar off screen
                // without dismissing it, and the offer used to be waiting again on the way back —
                // by then covering articles the reader had gone on to read deliberately.
                viewModel.dismissUndo()
            }
        }
    }

    // The empty state spells the failure out inline with a Retry next to it; over a list of
    // articles a snackbar is the only place a failed refresh can report itself.
    uiState.error?.let { message ->
        val errorMessage = message.resolve()
        LaunchedEffect(message) {
            if (articles.itemCount == 0) return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = retryActionLabel,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissError()
            if (result == SnackbarResult.ActionPerformed) viewModel.refreshFromNetwork()
        }
    }

    Scaffold(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!uiState.isOnline) {
                    OfflineBanner()
                }
                uiState.unavailableAiModelId?.let { modelId ->
                    AiModelUnavailableBanner(
                        modelId = modelId,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onDismiss = viewModel::dismissAiModelWarning
                    )
                }
                TopAppBar(
                    title = {
                        val listTitle = uiState.feedTitle
                            ?: stringResource(
                                if (uiState.starredOnly) R.string.main_starred_articles else R.string.main_all_articles
                            )
                        Text(
                            if (unreadCount > 0) {
                                stringResource(R.string.main_title_with_unread_count, listTitle, unreadCount)
                            } else {
                                listTitle
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onOpenSubscriptions,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.main_feeds),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val closing = searchActive.value
                                searchActive.value = !closing
                                if (closing) {
                                    viewModel.updateSearchQuery("")
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            AnimatedContent(
                                targetState = searchActive.value,
                                transitionSpec = {
                                    (fadeIn(
                                        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                                    ) + scaleIn(
                                        initialScale = 0.8f,
                                        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                                    )) togetherWith (fadeOut(
                                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                                    ) + scaleOut(
                                        targetScale = 0.8f,
                                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                                    ))
                                },
                                label = "search action icon"
                            ) { active ->
                                Icon(
                                    if (active) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = stringResource(
                                        if (active) R.string.main_close_search else R.string.action_search
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Always offered. Whether a network is usable is a guess the platform makes
                        // for us, and a reader who taps refresh has better information than a
                        // greyed-out button does; a genuinely unreachable server reports itself.
                        IconButton(
                            onClick = { if (!uiState.isRefreshing) viewModel.refreshFromNetwork() },
                            enabled = !uiState.isRefreshing,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.action_refresh),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        val expanded = remember { mutableStateOf(false) }
                        // The menu has to sit inside a box around its button. As a sibling in the
                        // action row it anchored to a zero-width slot after the button and opened
                        // adrift of the edge it belongs to.
                        Box {
                            IconButton(
                                onClick = { expanded.value = true },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.action_more),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = expanded.value,
                                onDismissRequest = { expanded.value = false },
                                // Clip first: a background painted before it keeps square corners.
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (uiState.starredOnly) R.string.main_show_all_articles
                                                else R.string.main_show_starred_articles
                                            ),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    onClick = {
                                        expanded.value = false
                                        viewModel.setStarredOnly(!uiState.starredOnly)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                                if (uiState.readCount > 0 || uiState.showReadArticles) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (uiState.showReadArticles) {
                                                    stringResource(R.string.main_unread_only)
                                                } else {
                                                    stringResource(R.string.main_show_read_articles, uiState.readCount)
                                                },
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        },
                                        onClick = {
                                            expanded.value = false
                                            viewModel.setShowReadArticles(!uiState.showReadArticles)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Done,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.main_settings), style = MaterialTheme.typography.labelLarge)
                                    },
                                    onClick = {
                                        expanded.value = false
                                        navController.navigate(Routes.SETTINGS)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(
                    visible = searchActive.value,
                    enter = expandVertically(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                    ) + fadeIn(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                    ) + fadeOut(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.main_search_articles)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                }
            }
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
                    onClick = { viewModel.markAllAsRead(onFeedMarkedRead) },
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
        val refreshState = articles.loadState.refresh
        when {
            // A skeleton in the shape of the list, rather than a spinner in the middle of nothing.
            refreshState is LoadState.Loading && articles.itemCount == 0 ->
                ArticleListSkeleton(modifier = Modifier.padding(paddingValues))

            refreshState is LoadState.Error && articles.itemCount == 0 -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                error = stringResource(R.string.main_could_not_read_stored_articles),
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                starredOnly = uiState.starredOnly,
                feedId = feedId,
                onRetry = { articles.retry() },
                onClearSearch = { viewModel.updateSearchQuery("") },
                onBrowseFeeds = onOpenSubscriptions,
                onAddFeed = { navController.navigate(Routes.addFeed()) },
                onBack = onLeaveFeed
            )

            articles.itemCount == 0 -> EmptyState(
                modifier = Modifier.padding(paddingValues),
                error = null,
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                starredOnly = uiState.starredOnly,
                feedId = feedId,
                onRetry = viewModel::refreshFromNetwork,
                onClearSearch = { viewModel.updateSearchQuery("") },
                onBrowseFeeds = onOpenSubscriptions,
                onAddFeed = { navController.navigate(Routes.addFeed()) },
                onBack = onLeaveFeed
            )

            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    if (uiState.isOnline) {
                        viewModel.refreshFromNetwork()
                    } else {
                        snackbarScope.launch {
                            snackbarHostState.showSnackbar(offlineRefreshMessage)
                        }
                    }
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
                                starredOnly = query.starredOnly,
                                includeRead = query.includeRead,
                                sessionStartMillis = query.sessionStart.toEpochMilli()
                            )
                        )
                    },
                    onCheckedChange = viewModel::updateEntryReadStatus
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(
    modifier: Modifier,
    error: String?,
    hasSearchQuery: Boolean,
    starredOnly: Boolean,
    feedId: Long?,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onBrowseFeeds: () -> Unit,
    onAddFeed: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                hasSearchQuery -> {
                    Text(
                        text = stringResource(R.string.main_nothing_matches_search),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Button(onClick = onClearSearch, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.main_clear_search))
                    }
                }

                starredOnly -> Text(
                    text = stringResource(R.string.main_no_starred_articles),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                else -> {
                    Text(
                        text = stringResource(
                            if (feedId == null) R.string.main_no_articles else R.string.main_no_feed_articles
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    // One filled button, like every other branch here. This one used to offer three
                    // of near-equal weight, so it read as a menu rather than a prompt; the two
                    // secondary routes stay reachable as text buttons.
                    Button(
                        onClick = { if (feedId == null) onBrowseFeeds() else onBack() },
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Text(
                            stringResource(
                                if (feedId == null) R.string.main_browse_subscriptions else R.string.main_back_to_all_articles
                            )
                        )
                    }
                    // Flow rather than Row: at a large font scale the two labels no longer fit
                    // side by side on a narrow screen, and a Row would clip them.
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onAddFeed) { Text(stringResource(R.string.main_add_subscription)) }
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.main_refresh_now)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.main_offline_banner),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AiModelUnavailableBanner(
    modelId: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.main_ai_model_unavailable_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.main_ai_model_unavailable_message, modelId),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_show_settings)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}
