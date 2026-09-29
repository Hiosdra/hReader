package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.isRead
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.navigation.ArticleRouteArguments
import com.hiosdra.hreader.presentation.text.resolve
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

internal fun initialArticlePagerPage(currentIndex: Int, entryCount: Int): Int? =
    currentIndex.takeIf { it in 0 until entryCount }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    routeArguments: ArticleRouteArguments,
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
    val displayPreferences by viewModel.displayPreferences.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val pagerState = rememberPagerState(initialPage = 0) { navigation.entries.size }
    val effectChannel = remember { Channel<ArticleRouteEffect>(Channel.BUFFERED) }
    val effects = remember(effectChannel) { effectChannel.receiveAsFlow() }
    val dispatchEffect: (ArticleRouteEffect) -> Unit = remember(effectChannel) {
        { effect -> effectChannel.trySend(effect) }
    }
    var isWebViewMode by remember { mutableStateOf(false) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    var pagerPositioned by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
    val onFeedback: (FeedbackRequest) -> Unit = { request ->
        feedbackScope.launch { snackbarHostState.showFeedback(request) }
    }
    fun reloadArticleList() {
        viewModel.openList(
            feedId = routeArguments.feedId,
            startArticleId = routeArguments.startArticleId,
            includeRead = routeArguments.includeRead,
            sessionStartMillis = routeArguments.sessionStartMillis
        )
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
    val configuredTtsModel = displayPreferences.ttsModel
    val configuredPaywallBypassMethod = displayPreferences.defaultPaywallBypassMethod
    var temporaryTtsModel by remember { mutableStateOf<TtsModel?>(null) }
    var ttsPlayerSheetVisible by rememberSaveable { mutableStateOf(false) }
    var paywallMethodPickerVisible by rememberSaveable { mutableStateOf(false) }
    var ttsMiniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    LaunchedEffect(routeArguments) {
        reloadArticleList()
    }
    LaunchedEffect(currentRoute) {
        viewModel.refreshDisplayPreferences()
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

    LaunchedEffect(navigation.entries.size, navigation.currentIndex) {
        val initialPage = initialArticlePagerPage(
            currentIndex = navigation.currentIndex,
            entryCount = navigation.entries.size
        )
        if (pagerPositioned || initialPage == null) return@LaunchedEffect
        pagerState.scrollToPage(initialPage)
        pagerPositioned = true
    }

    LaunchedEffect(pagerPositioned) {
        if (!pagerPositioned) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val entry = viewModel.uiState.value.navigation.entries.getOrNull(page) ?: return@collect
            if (ttsController.state.value.articleId?.let { it != entry.id } == true) {
                ttsController.pause()
            }
            if (shouldAutomaticallyMarkRead(entry)) {
                viewModel.updateReadStatus(page, true)
            }
        }
    }

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
        uiState.displayedProvenance(entry, currentWebViewActive)
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

    val topBarState = ArticleScreenTopBarState(
        entryUrl = currentEntry?.url,
        feedTitle = currentEntry?.feed?.title,
        listPosition = navigation.currentListPosition,
        listSize = navigation.listSize,
        isWebViewMode = currentWebViewActive,
        canUseWebView = currentWebViewAvailable,
        isRead = currentEntry?.isRead == true,
        textScale = textScale,
        ttsContentState = ttsContentState,
        isTtsActive = ttsState.articleId != null,
        isOnline = content.isOnline,
        defaultPaywallBypassMethod = configuredPaywallBypassMethod,
        canUsePaywallBypass = canUsePaywallBypass,
        displayedProvenance = currentDisplayedProvenance
    )
    val topBarActions = ArticleScreenTopBarActions(
        onDecreaseTextScale = {
            textScale = (textScale - ARTICLE_TEXT_SCALE_STEP).coerceAtLeast(MIN_ARTICLE_TEXT_SCALE)
        },
        onResetTextScale = { textScale = 1f },
        onIncreaseTextScale = {
            textScale = (textScale + ARTICLE_TEXT_SCALE_STEP).coerceAtMost(MAX_ARTICLE_TEXT_SCALE)
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
        onInvokeTts = {
            if (ttsState.articleId != null) {
                ttsPlayerSheetVisible = true
            } else {
                currentEntry?.takeIf {
                    ttsContentState == ArticleTtsContentState.AVAILABLE
                }?.let { playArticleTts(it) }
            }
        },
        onOpenInChrome = { currentEntry?.url?.let(openArticleInChrome) },
        onBypassPaywall = { method ->
            currentEntry?.url?.let { url -> openArticleThroughPaywall(url, method) }
        },
        onOpenPaywallMethodPicker = { paywallMethodPickerVisible = true },
        onSwitchToFeed = { isWebViewMode = false }
    )
    val pagerBindings = ArticlePagerBindings(
        state = uiState,
        bionicReadingEnabled = displayPreferences.bionicReadingEnabled,
        articleImageLoader = articleImageLoader,
        coilImageLoader = coilImageLoader,
        remoteResourcePolicy = remoteResourcePolicy,
        onReadingProgressChanged = viewModel::saveReadingProgress,
        onReadingCompleted = viewModel::clearReadingProgress,
        onRetryContent = viewModel::retryContent,
        onEffect = dispatchEffect,
        onAiOverview = viewModel::generateAiOverview,
        onAnalyzeCredibility = viewModel::analyzeCredibility,
        defaultPaywallBypassMethod = configuredPaywallBypassMethod,
        canUsePaywallBypass = { url ->
            url.isNotBlank() && !paywallBypassService.isPaywallBypassUrl(url)
        },
        onOpenInChrome = openArticleInChrome,
        onBypassPaywall = openArticleThroughPaywall
    )
    ArticleScreenPresentation(
        state = ArticleScreenPresentationState(
            navigation = navigation,
            content = content,
            ai = ai,
            currentEntry = currentEntry,
            currentWebViewActive = currentWebViewActive,
            textScale = textScale,
            topBar = topBarState,
            topBarActions = topBarActions,
            pagerState = pagerState,
            pagerBindings = pagerBindings,
            bottomContentPadding = articleBottomContentPadding,
            snackbarHostState = snackbarHostState,
            ttsState = ttsState,
            ttsPlayerContentState = ttsPlayerContentState,
            ttsPlayerSheetVisible = ttsPlayerSheetVisible,
            temporaryTtsModel = temporaryTtsModel,
            configuredTtsModel = configuredTtsModel,
            ttsModelStatuses = ttsModelStatuses,
            paywallMethodPickerVisible = paywallMethodPickerVisible,
            defaultPaywallBypassMethod = configuredPaywallBypassMethod
        ),
        actions = ArticleScreenPresentationActions(
            onRetryNavigation = { reloadArticleList() },
            onGenerateAiOverview = viewModel::generateAiOverview,
            onClearOverviewError = viewModel::clearOverviewError,
            onRetryContent = viewModel::retryContent,
            onClearContentError = viewModel::clearContentError,
            onAnalyzeCredibility = { id ->
                viewModel.analyzeCredibility(id, forceRefresh = true)
            },
            onClearScoreError = viewModel::clearScoreError,
            onOpenTtsPlayer = { ttsPlayerSheetVisible = true },
            onPauseTts = ttsController::pause,
            onResumeTts = ttsController::resume,
            onStopTts = {
                ttsPlayerSheetVisible = false
                ttsController.stop()
            },
            onTtsMiniPlayerSizeChanged = { height ->
                if (ttsMiniPlayerHeightPx != height) ttsMiniPlayerHeightPx = height
            },
            onTemporaryTtsModelChanged = { model ->
                ttsController.stop()
                temporaryTtsModel = model
            },
            onRetryTts = retryTts,
            onDismissTtsPlayer = { ttsPlayerSheetVisible = false },
            onSelectPaywallMethod = { method ->
                paywallMethodPickerVisible = false
                currentEntry?.url?.let { url -> openArticleThroughPaywall(url, method) }
            },
            onDismissPaywallPicker = { paywallMethodPickerVisible = false }
        )
    )
}
