package com.hiosdra.hreader.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.hiosdra.hreader.navigation.Routes
import com.hiosdra.hreader.ui.article.ArticleListGrouped
import com.hiosdra.hreader.ui.components.ArticleListSkeleton
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    onOpenSubscriptions: () -> Unit,
    feedId: Long? = null,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val articles = viewModel.articles.collectAsLazyPagingItems()

    LaunchedEffect(feedId) { viewModel.setFeed(feedId) }

    // The counter and "mark all read" are about the whole list, which a search is not showing.
    // Left visible they offered to mark hundreds of articles the reader could not see.
    val isSearching = uiState.searchQuery.isNotBlank()
    val unreadCount = if (isSearching) 0 else uiState.unreadCount
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val searchActive = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    // Actions that already happened are offered back, instead of being asked about beforehand.
    uiState.undo?.let { undo ->
        LaunchedEffect(undo.id) {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = undo.message,
                    actionLabel = "Undo",
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
        LaunchedEffect(message) {
            if (articles.itemCount == 0) return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Retry",
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
                            ?: if (uiState.starredOnly) "Starred" else "All Items"
                        Text(
                            listTitle + if (unreadCount > 0) "  •  $unreadCount" else "",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (feedId == null) {
                            IconButton(
                                onClick = onOpenSubscriptions,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = "Feeds",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { searchActive.value = !searchActive.value },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                if (searchActive.value) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchActive.value) "Close search" else "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
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
                                    contentDescription = "Refresh",
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
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = expanded.value,
                                onDismissRequest = { expanded.value = false },
                                // Clip first: a background painted before it keeps square corners.
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.starredOnly) "All articles" else "Starred only",
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
                                                    "Unread only"
                                                } else {
                                                    "Show read articles (${uiState.readCount})"
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
                                    text = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
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
                AnimatedVisibility(visible = searchActive.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search articles") },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = unreadCount > 0,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::markAllAsRead,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    text = { Text(text = "Mark $unreadCount read") },
                    expanded = unreadCount < 100,
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
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
                error = refreshState.error.message ?: "Could not read the stored articles",
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                starredOnly = uiState.starredOnly,
                feedId = feedId,
                onRetry = { articles.retry() },
                onClearSearch = { viewModel.updateSearchQuery("") },
                onBrowseFeeds = onOpenSubscriptions,
                onAddFeed = { navController.navigate(Routes.addFeed()) },
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
            )

            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    if (uiState.isOnline) {
                        viewModel.refreshFromNetwork()
                    } else {
                        snackbarScope.launch {
                            snackbarHostState.showSnackbar("Offline — connect to a network to refresh.")
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
                    ElevatedButton(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
                        Text("Retry")
                    }
                }

                hasSearchQuery -> {
                    Text(
                        text = "Nothing matches that search",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    OutlinedButton(onClick = onClearSearch, modifier = Modifier.padding(top = 20.dp)) {
                        Text("Clear search")
                    }
                }

                starredOnly -> Text(
                    text = "No starred articles yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                else -> {
                    Text(
                        text = if (feedId == null) "No articles yet" else "No articles for this feed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    ElevatedButton(
                        onClick = { if (feedId == null) onBrowseFeeds() else onBack() },
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Text(if (feedId == null) "Browse subscriptions" else "Back to all items")
                    }
                    OutlinedButton(onClick = onAddFeed, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Add subscription")
                    }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Refresh now")
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
            text = "Offline — showing what was downloaded. Anything you read syncs when you are back.",
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
                text = "AI model unavailable",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "OpenRouter no longer offers $modelId. Article overviews and credibility " +
                    "scoring will fail until you pick another model.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) { Text("Open settings") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
