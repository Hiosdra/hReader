package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.navigation.NavHostController
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.isRead
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.navigation.ArticleRouteArguments
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.R
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal const val MIN_ARTICLE_TEXT_SCALE = 0.85f
internal const val MAX_ARTICLE_TEXT_SCALE = 1.35f
private const val ARTICLE_TEXT_SCALE_STEP = 0.1f
internal const val FEED_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.72f
internal const val WEB_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.85f
internal const val READING_POSITION_SAMPLE_MILLIS = 400L

// Compose packs layout dimensions into 18 bits. Keep a margin below the 262143 px
// representable maximum because Modifier.height converts Dp back to integer pixels.
internal const val MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX = 262_000

internal fun safeArticleWebViewHeightPx(contentHeightPx: Int): Int =
    contentHeightPx.coerceIn(0, MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX)

internal fun articleWebViewNeedsInternalScroll(contentHeightPx: Int): Boolean =
    contentHeightPx > MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX

internal fun articleWebViewRestoreScrollY(
    progress: Float,
    contentHeightPx: Int,
    viewportHeightPx: Int
): Int {
    val maxScrollY = readerWebViewMaxScrollPx(contentHeightPx, viewportHeightPx)
    return (progress.coerceIn(0f, 1f) * maxScrollY).roundToInt()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    routeArguments: ArticleRouteArguments,
    readerPreferences: ReaderPreferences,
    ttsPreferences: TtsPreferences,
    paywallBypassService: PaywallBypass,
    ttsModelManager: TtsModelGateway,
    ttsController: ArticleTtsPlayer,
    articleImageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
    articleImageSharer: ArticleImageSharer,
    articleImageDownloader: ArticleImageDownloader,
    viewModel: ArticleViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = uiState.navigation
    val content = uiState.content
    val ai = uiState.ai
    val pagerState = rememberPagerState(initialPage = 0) { navigation.entries.size }
    val effectChannel = remember { Channel<ArticleRouteEffect>(Channel.BUFFERED) }
    val effects = remember(effectChannel) { effectChannel.receiveAsFlow() }
    val dispatchEffect: (ArticleRouteEffect) -> Unit = remember(effectChannel) {
        { effect -> effectChannel.trySend(effect) }
    }
    var isWebViewMode by remember { mutableStateOf(false) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    // The pager opens on page 0 and only then jumps to the article being read, so
    // neither read state nor the reader's position may be touched before it lands.
    var pagerPositioned by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
    val onFeedback: (FeedbackRequest) -> Unit = { request ->
        feedbackScope.launch { snackbarHostState.showFeedback(request) }
    }
    ArticleRouteEffectHost(
        effects = effects,
        context = navController.context,
        paywallBypassService = paywallBypassService,
        articleImageSharer = articleImageSharer,
        articleImageDownloader = articleImageDownloader,
        onFeedback = onFeedback
    )

    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    val ttsModelStatuses by ttsModelManager.statuses.collectAsStateWithLifecycle()
    val configuredTtsModel = ttsPreferences.getTtsModel()
    val configuredPaywallBypassMethod = readerPreferences.getPaywallBypassMethod()
    var temporaryTtsModel by remember { mutableStateOf<TtsModel?>(null) }
    var ttsPlayerSheetVisible by rememberSaveable { mutableStateOf(false) }
    var paywallMethodPickerVisible by rememberSaveable { mutableStateOf(false) }
    var ttsMiniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    LaunchedEffect(routeArguments) {
        viewModel.openList(
            feedId = routeArguments.feedId,
            startArticleId = routeArguments.startArticleId,
            includeRead = routeArguments.includeRead,
            sessionStartMillis = routeArguments.sessionStartMillis
        )
    }

    val currentOfflinePageAvailable = navigation.entries
        .getOrNull(navigation.currentIndex)
        ?.id
        ?.let(content.offlinePages::containsKey) == true
    LaunchedEffect(content.isOnline, navigation.currentIndex, currentOfflinePageAvailable) {
        if (!content.isOnline && !currentOfflinePageAvailable) {
            isWebViewMode = false
        }
    }

    // The view model owns where the reader is, so it also survives a configuration
    // change; the pager is placed from it once and reports back from then on.
    LaunchedEffect(navigation.entries.size) {
        if (pagerPositioned || navigation.entries.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(navigation.currentIndex.coerceIn(navigation.entries.indices))
        pagerPositioned = true
    }

    // Read state follows the page the pager settles on. Pages that are merely
    // composed - the ones passed on the way to the opened article, or a neighbour
    // revealed by a swipe that snaps back - stay unread.
    LaunchedEffect(pagerPositioned) {
        if (!pagerPositioned) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val entry = viewModel.uiState.value.navigation.entries.getOrNull(page) ?: return@collect
            if (ttsController.state.value.articleId?.let { it != entry.id } == true) {
                ttsController.pause()
            }
            if (!entry.isRead) {
                viewModel.updateReadStatus(page, true)
            }
        }
    }

    // Reporting back only once the pager has been placed keeps page 0 - where it
    // still sits on the first frame - from overwriting the position it was sent to.
    LaunchedEffect(pagerState.settledPage, pagerPositioned) {
        if (!pagerPositioned) return@LaunchedEffect
        if (pagerState.settledPage != navigation.currentIndex && pagerState.settledPage in navigation.entries.indices) {
            viewModel.setCurrentIndex(pagerState.settledPage)
        }
    }

    val currentEntry = navigation.entries.getOrNull(navigation.currentIndex)
    val currentOfflinePage = currentEntry?.let { content.offlinePages[it.id] }
    val currentWebViewAvailable = content.isOnline || currentOfflinePage != null
    val currentWebViewActive = isWebViewMode && currentWebViewAvailable
    val currentDisplayedProvenance = currentEntry?.let { entry ->
        when {
            currentWebViewActive && content.isOnline -> ArticleContentProvenance(
                kind = ArticleContentKind.EXTERNAL_WEB_PAGE,
                sourceUrl = entry.url,
                delivery = ArticleContentDelivery.NETWORK
            )
            currentWebViewActive && currentOfflinePage != null -> ArticleContentProvenance(
                kind = ArticleContentKind.SAVED_WEB_PAGE,
                sourceUrl = currentOfflinePage.finalUrl.ifBlank { currentOfflinePage.originalUrl },
                fetchedAt = currentOfflinePage.fetchedAt,
                delivery = ArticleContentDelivery.LOCAL_STORAGE,
                isComplete = currentOfflinePage.isComplete
            )
            else -> viewModel.getContentProvenanceForEntry(entry.id)
        }
    }
    val ttsContent = currentEntry?.let { viewModel.getContentForEntry(it.id) }
    val contentLoadFinished = currentEntry?.let { entry ->
        viewModel.getContentStateForEntry(entry.id) != ArticleContentLoadState.LOADING
    } == true
    val ttsContentState = if (currentEntry == null) {
        null
    } else {
        remember(currentEntry.id, ttsContent, contentLoadFinished) {
            articleTtsContentState(
                content = ttsContent,
                contentLoadFinished = contentLoadFinished
            )
        }
    }
    val ttsPlayerContent = ttsState.articleId?.let(viewModel::getContentForEntry)
    val ttsPlayerContentLoadFinished = ttsState.articleId?.let { articleId ->
        viewModel.getContentStateForEntry(articleId) != ArticleContentLoadState.LOADING
    } == true
    val ttsPlayerContentState = if (ttsState.articleId == null) {
        null
    } else {
        remember(ttsState.articleId, ttsPlayerContent, ttsPlayerContentLoadFinished) {
            articleTtsContentState(
                content = ttsPlayerContent,
                contentLoadFinished = ttsPlayerContentLoadFinished
            )
        }
    }
    LaunchedEffect(ttsState.articleId) {
        if (ttsState.articleId == null) {
            ttsPlayerSheetVisible = false
            ttsMiniPlayerHeightPx = 0
        }
    }
    val ttsMiniPlayerHeight = with(LocalDensity.current) { ttsMiniPlayerHeightPx.toDp() }
    val articleBottomContentPadding = if (ttsState.articleId != null) {
        ttsMiniPlayerHeight + 16.dp
    } else {
        0.dp
    }
    val playArticleTts: (Entry) -> Unit = { entry ->
        requestNotificationPermission {
            viewModel.getContentForEntry(entry.id)
                ?.takeIf(::hasReadableArticleText)
                ?.let { html ->
                    ttsController.play(
                        articleId = entry.id,
                        title = entry.title,
                        html = html,
                        modelOverride = temporaryTtsModel
                    )
                }
        }
    }
    val retryTts = {
        ttsState.articleId
            ?.let { articleId -> navigation.entries.firstOrNull { it.id == articleId } }
            ?.let { entry ->
                if (ttsPlayerContent?.let(::hasReadableArticleText) == true) {
                    playArticleTts(entry)
                } else {
                    viewModel.retryContent(entry.id)
                }
            }
        Unit
    }
    val openArticleInChrome: (String) -> Unit = { url ->
        if (content.isOnline && url.isNotBlank()) {
            dispatchEffect(ArticleRouteEffect.OpenBrowser(url))
        }
    }
    val openArticleThroughPaywall: (String, PaywallBypassMethod) -> Unit = { url, method ->
        if (content.isOnline && url.isNotBlank()) {
            dispatchEffect(ArticleRouteEffect.OpenPaywall(url, method))
        }
    }
    val canUsePaywallBypass = currentEntry?.url?.let { url ->
        url.isNotBlank() && !paywallBypassService.isPaywallBypassUrl(url)
    } == true

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = articleBottomContentPadding)
            )
        },
        topBar = {
            Column {
                ArticleTopBar(
                    entryUrl = currentEntry?.url,
                    feedTitle = currentEntry?.feed?.title,
                    listPosition = navigation.currentListPosition,
                    listSize = navigation.listSize,
                    isWebViewMode = currentWebViewActive,
                    canUseWebView = currentWebViewAvailable,
                    isRead = currentEntry?.isRead == true,
                    textScale = textScale,
                    onDecreaseTextScale = {
                        textScale = (textScale - ARTICLE_TEXT_SCALE_STEP)
                            .coerceAtLeast(MIN_ARTICLE_TEXT_SCALE)
                    },
                    onResetTextScale = { textScale = 1f },
                    onIncreaseTextScale = {
                        textScale = (textScale + ARTICLE_TEXT_SCALE_STEP)
                            .coerceAtMost(MAX_ARTICLE_TEXT_SCALE)
                    },
                    onToggleRead = {
                        currentEntry?.let { entry ->
                            viewModel.updateReadStatus(
                                index = navigation.currentIndex,
                                isRead = !entry.isRead
                            )
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onToggleWebView = { isWebViewMode = !isWebViewMode },
                    onShare = {
                        currentEntry?.let { entry ->
                            dispatchEffect(ArticleRouteEffect.ShareArticle(entry.title, entry.url))
                        }
                    },
                    ttsContentState = ttsContentState,
                    isTtsActive = ttsState.articleId != null,
                    onInvokeTts = {
                        if (ttsState.articleId != null) {
                            ttsPlayerSheetVisible = true
                        } else {
                            currentEntry?.takeIf {
                                ttsContentState == ArticleTtsContentState.AVAILABLE
                            }?.let { playArticleTts(it) }
                        }
                    },
                    isOnline = content.isOnline,
                    defaultPaywallBypassMethod = configuredPaywallBypassMethod,
                    canUsePaywallBypass = canUsePaywallBypass,
                    onOpenInChrome = {
                        currentEntry?.url?.let(openArticleInChrome)
                    },
                    onBypassPaywall = { method ->
                        currentEntry?.url?.let { url -> openArticleThroughPaywall(url, method) }
                    },
                    onOpenPaywallMethodPicker = { paywallMethodPickerVisible = true }
                )
                if (currentWebViewActive && currentDisplayedProvenance != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArticleContentProvenanceStatus(
                            provenance = currentDisplayedProvenance,
                            onSwitchToFeed = { isWebViewMode = false }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                navigation.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = articleBottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                navigation.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = articleBottomContentPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = navigation.error?.resolve().orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = {
                                    viewModel.openList(
                                        feedId = routeArguments.feedId,
                                        startArticleId = routeArguments.startArticleId,
                                        includeRead = routeArguments.includeRead,
                                        sessionStartMillis = routeArguments.sessionStartMillis
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                navigation.entries.isNotEmpty() -> {
                    ArticlePager(
                        entries = navigation.entries,
                        pagerState = pagerState,
                        isWebViewMode = currentWebViewActive,
                        textScale = textScale,
                        paddingValues = paddingValues,
                        bottomContentPadding = articleBottomContentPadding,
                        getContentForEntry = { entryId -> viewModel.getContentForEntry(entryId) },
                        getContentStateForEntry = { entryId -> viewModel.getContentStateForEntry(entryId) },
                        getContentProvenanceForEntry = { entryId ->
                            viewModel.getContentProvenanceForEntry(entryId)
                        },
                        getLeadImageForEntry = { entryId -> viewModel.getLeadImageForEntry(entryId) },
                        getOfflinePageForEntry = { entryId -> viewModel.getOfflinePageForEntry(entryId) },
                        loadedContentIds = content.content.keys,
                        loadedReadingPositionIds = content.readingProgress.loadedIds,
                        readingProgressForEntry = { entryId -> viewModel.getReadingProgressForEntry(entryId) },
                        onReadingProgressChanged = viewModel::saveReadingProgress,
                        onReadingCompleted = viewModel::clearReadingProgress,
                        onRetryContent = viewModel::retryContent,
                        readerPreferences = readerPreferences,
                        articleImageLoader = articleImageLoader,
                        coilImageLoader = coilImageLoader,
                        remoteResourcePolicy = remoteResourcePolicy,
                        onEffect = dispatchEffect,
                        localImagePaths = content.localImagePaths,
                        isOnline = content.isOnline,
                        aiOverviews = ai.aiOverviews,
                        aiProvider = ai.aiProvider,
                        generatingOverviewIds = ai.generatingOverviewIds,
                        aiOverviewProgress = ai.aiOverviewProgress,
                        onAiOverview = { entryId -> viewModel.generateAiOverview(entryId) },
                        credibilityEnabled = ai.credibilityEnabled,
                        credibilityReports = ai.credibilityReports,
                        analyzingCredibilityIds = ai.analyzingCredibilityIds,
                        onAnalyzeCredibility = { entryId, force -> viewModel.analyzeCredibility(entryId, force) },
                        defaultPaywallBypassMethod = configuredPaywallBypassMethod,
                        canUsePaywallBypass = { url ->
                            url.isNotBlank() && !paywallBypassService.isPaywallBypassUrl(url)
                        },
                        onOpenInChrome = openArticleInChrome,
                        onBypassPaywall = openArticleThroughPaywall
                    )
                }
            }

            val currentEntryId = navigation.entries.getOrNull(navigation.currentIndex)?.id

            ai.overviewError?.let { error ->
                RetryableSnackbar(
                    hostState = snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null
                    },
                    onAction = { currentEntryId?.let { viewModel.generateAiOverview(it) } },
                    onDismissed = viewModel::clearOverviewError
                )
            }

            content.contentError?.let { message ->
                if (currentEntryId != null) {
                    ArticleContentErrorBanner(
                        message = message.resolve(),
                        onRetry = { viewModel.retryContent(currentEntryId) },
                        onDismiss = viewModel::clearContentError,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = articleBottomContentPadding + 8.dp)
                            .navigationBarsPadding()
                    )
                }
            }

            ai.scoreError?.let { error ->
                RetryableSnackbar(
                    hostState = snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null
                    },
                    onAction = { currentEntryId?.let { viewModel.analyzeCredibility(it, forceRefresh = true) } },
                    onDismissed = viewModel::clearScoreError
                )
            }

            if (ttsState.articleId != null) {
                ArticleTtsMiniPlayer(
                    state = ttsState,
                    onOpen = { ttsPlayerSheetVisible = true },
                    onPause = ttsController::pause,
                    onResume = ttsController::resume,
                    onStop = ttsController::stop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .navigationBarsPadding(),
                    onSizeChanged = { height ->
                        if (ttsMiniPlayerHeightPx != height) ttsMiniPlayerHeightPx = height
                    }
                )
            }

            if (ttsPlayerSheetVisible && ttsState.articleId != null) {
                ArticleTtsPlayerSheet(
                    state = ttsState,
                    temporaryModel = temporaryTtsModel,
                    configuredModel = configuredTtsModel,
                    modelStatuses = ttsModelStatuses,
                    contentState = ttsPlayerContentState,
                    onTemporaryModelChange = { model ->
                        ttsController.stop()
                        temporaryTtsModel = model
                    },
                    onPause = ttsController::pause,
                    onResume = ttsController::resume,
                    onStop = {
                        ttsPlayerSheetVisible = false
                        ttsController.stop()
                    },
                    onRetry = retryTts,
                    onDismiss = { ttsPlayerSheetVisible = false }
                )
            }

            if (paywallMethodPickerVisible && currentEntry != null) {
                PaywallBypassMethodPicker(
                    defaultPaywallBypassMethod = configuredPaywallBypassMethod,
                    onSelect = { method ->
                        paywallMethodPickerVisible = false
                        currentEntry.url.let { url -> openArticleThroughPaywall(url, method) }
                    },
                    onDismiss = { paywallMethodPickerVisible = false }
                )
            }
        }
    }
}

@Composable
private fun ArticleContentErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Shows [message] once and clears it, whether the reader acted on it or let it time out. Keyed on
 * the message so a second, different failure is announced rather than swallowed.
 */
@Composable
private fun RetryableSnackbar(
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismissed: () -> Unit
) {
    LaunchedEffect(message) {
        hostState.showFeedback(
            FeedbackRequest(
                message = message,
                kind = FeedbackKind.RECOVERABLE_ERROR,
                actionLabel = actionLabel,
                onAction = onAction
            )
        )
        onDismissed()
    }
}
