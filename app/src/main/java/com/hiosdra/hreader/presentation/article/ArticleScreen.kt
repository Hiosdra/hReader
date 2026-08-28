package com.hiosdra.hreader.presentation.article

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.domain.model.isRead
import com.hiosdra.hreader.core.domain.service.cleanUrl
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.presentation.theme.MotionDuration
import com.hiosdra.hreader.R
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

internal const val MIN_ARTICLE_TEXT_SCALE = 0.85f
internal const val MAX_ARTICLE_TEXT_SCALE = 1.35f
private const val ARTICLE_TEXT_SCALE_STEP = 0.1f
internal const val FEED_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.72f
internal const val WEB_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.85f
internal const val ARTICLE_BOTTOM_BAR_ALPHA = 0.72f
internal const val READING_POSITION_SAMPLE_MILLIS = 400L

// Compose packs layout dimensions into 18 bits. Keep a margin below the 262143 px
// representable maximum because Modifier.height converts Dp back to integer pixels.
internal const val MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX = 262_000
internal const val ARTICLE_WEB_VIEW_INTERNAL_SCROLL_VIEWPORT_MULTIPLIER = 4

internal fun safeArticleWebViewHeightPx(contentHeightPx: Int): Int =
    contentHeightPx.coerceIn(0, MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX)

internal fun articleWebViewNeedsInternalScroll(contentHeightPx: Int): Boolean =
    contentHeightPx > MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX

internal fun articleWebViewNeedsInternalScroll(
    contentHeightPx: Int,
    viewportHeightPx: Int
): Boolean {
    if (contentHeightPx <= 0) return false
    if (contentHeightPx > MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX) return true
    val viewportLimit = viewportHeightPx.toLong() * ARTICLE_WEB_VIEW_INTERNAL_SCROLL_VIEWPORT_MULTIPLIER
    return viewportHeightPx > 0 && contentHeightPx.toLong() > viewportLimit
}

internal fun articleWebViewRestoreScrollY(
    progress: Float,
    contentHeightPx: Int,
    viewportHeightPx: Int
): Int {
    val maxScrollY = (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
    return (progress.coerceIn(0f, 1f) * maxScrollY).roundToInt()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    feedId: Long?,
    startArticleId: Long,
    starredOnly: Boolean = false,
    includeRead: Boolean = false,
    sessionStartMillis: Long = 0L,
    preferencesManager: AppPreferences,
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
    val pagerState = rememberPagerState(initialPage = 0) { uiState.entries.size }
    var isWebViewMode by remember { mutableStateOf(false) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    // The pager opens on page 0 and only then jumps to the article being read, so
    // neither read state nor the reader's position may be touched before it lands.
    var pagerPositioned by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    val ttsModelStatuses by ttsModelManager.statuses.collectAsStateWithLifecycle()
    val configuredTtsModel = preferencesManager.getTtsModel()
    var temporaryTtsModel by remember { mutableStateOf<TtsModel?>(null) }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    LaunchedEffect(feedId, startArticleId, starredOnly, includeRead, sessionStartMillis) {
        viewModel.openList(feedId, startArticleId, starredOnly, includeRead, sessionStartMillis)
    }

    val currentOfflinePageAvailable = uiState.entries
        .getOrNull(uiState.currentIndex)
        ?.id
        ?.let(uiState.offlinePages::containsKey) == true
    LaunchedEffect(uiState.isOnline, uiState.currentIndex, currentOfflinePageAvailable) {
        if (!uiState.isOnline && !currentOfflinePageAvailable) {
            isWebViewMode = false
        }
    }

    // The view model owns where the reader is, so it also survives a configuration
    // change; the pager is placed from it once and reports back from then on.
    LaunchedEffect(uiState.entries.size) {
        if (pagerPositioned || uiState.entries.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(uiState.currentIndex.coerceIn(uiState.entries.indices))
        pagerPositioned = true
    }

    // Read state follows the page the pager settles on. Pages that are merely
    // composed - the ones passed on the way to the opened article, or a neighbour
    // revealed by a swipe that snaps back - stay unread.
    LaunchedEffect(pagerPositioned) {
        if (!pagerPositioned) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val entry = viewModel.uiState.value.entries.getOrNull(page) ?: return@collect
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
        if (pagerState.settledPage != uiState.currentIndex && pagerState.settledPage in uiState.entries.indices) {
            viewModel.setCurrentIndex(pagerState.settledPage)
        }
    }

    val currentEntry = uiState.entries.getOrNull(uiState.currentIndex)
    var bottomActionBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomActionBarHeight = if (currentEntry == null) {
        0.dp
    } else {
        with(LocalDensity.current) { bottomActionBarHeightPx.toDp() }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomActionBarHeight)
            )
        },
        topBar = {
            ArticleTopBar(
                entryUrl = currentEntry?.url,
                feedTitle = currentEntry?.feed?.title,
                listPosition = uiState.currentListPosition,
                listSize = uiState.listSize,
                isWebViewMode = isWebViewMode,
                canUseWebView = uiState.isOnline ||
                    (currentEntry?.id?.let { uiState.offlinePages.containsKey(it) } == true),
                isStarred = currentEntry?.starred == true,
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
                onToggleStar = {
                    currentEntry?.let { entry -> viewModel.setStarred(entry.id, !entry.starred) }
                },
                onToggleRead = {
                    currentEntry?.let { entry ->
                        viewModel.updateReadStatus(
                            index = uiState.currentIndex,
                            isRead = !entry.isRead
                        )
                    }
                },
                onBack = { navController.popBackStack() },
                onToggleWebView = { isWebViewMode = !isWebViewMode },
                onShare = {
                    currentEntry?.let { entry ->
                        val ctx = navController.context
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, entry.title)
                            putExtra(Intent.EXTRA_TEXT, "${entry.title}\n${cleanUrl(entry.url)}")
                        }
                        ctx.startActivity(Intent.createChooser(sendIntent, null))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = bottomActionBarHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = bottomActionBarHeight)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error?.resolve().orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                uiState.entries.isNotEmpty() -> {
                    ArticlePager(
                        entries = uiState.entries,
                        pagerState = pagerState,
                        isWebViewMode = isWebViewMode,
                        textScale = textScale,
                        paddingValues = paddingValues,
                        getContentForEntry = { entryId -> viewModel.getContentForEntry(entryId) },
                        getLeadImageForEntry = { entryId -> viewModel.getLeadImageForEntry(entryId) },
                        getOfflinePageForEntry = { entryId -> viewModel.getOfflinePageForEntry(entryId) },
                        loadedContentIds = uiState.content.keys,
                        loadedReadingPositionIds = uiState.readingPositionLoadedIds,
                        readingProgressForEntry = { entryId -> viewModel.getReadingProgressForEntry(entryId) },
                        onReadingProgressChanged = viewModel::saveReadingProgress,
                        onReadingCompleted = viewModel::clearReadingProgress,
                        preferencesManager = preferencesManager,
                        articleImageLoader = articleImageLoader,
                        coilImageLoader = coilImageLoader,
                        remoteResourcePolicy = remoteResourcePolicy,
                        articleImageSharer = articleImageSharer,
                        articleImageDownloader = articleImageDownloader,
                        localImagePaths = uiState.localImagePaths,
                        isOnline = uiState.isOnline,
                        aiOverviews = uiState.aiOverviews,
                        aiProvider = uiState.aiProvider,
                        generatingOverviewIds = uiState.generatingOverviewIds,
                        aiOverviewProgress = uiState.aiOverviewProgress,
                        onAiOverview = { entryId -> viewModel.generateAiOverview(entryId) },
                        credibilityEnabled = uiState.credibilityEnabled,
                        credibilityReports = uiState.credibilityReports,
                        analyzingCredibilityIds = uiState.analyzingCredibilityIds,
                        onAnalyzeCredibility = { entryId, force -> viewModel.analyzeCredibility(entryId, force) }
                    )
                }
            }

            // Errors interrupt reading as little as possible: a modal with a single OK button stopped
            // the article dead to report something the reader can act on later, or not at all.
            val currentEntryId = uiState.entries.getOrNull(uiState.currentIndex)?.id

            uiState.overviewError?.let { error ->
                RetryableSnackbar(
                    hostState = snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null && uiState.aiProvider == AiProvider.GEMMA_LOCAL
                    },
                    onAction = { currentEntryId?.let { viewModel.generateAiOverview(it) } },
                    onDismissed = viewModel::clearOverviewError
                )
            }

            // What is missing offline, said out loud rather than left as an empty screen.
            uiState.contentError?.let { message ->
                if (currentEntryId != null) {
                    ArticleContentErrorBanner(
                        message = message.resolve(),
                        onRetry = { viewModel.retryContent(currentEntryId) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomActionBarHeight + 8.dp)
                            .navigationBarsPadding()
                    )
                }
            }

            uiState.scoreError?.let { error ->
                RetryableSnackbar(
                    hostState = snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null && uiState.aiProvider == AiProvider.GEMMA_LOCAL
                    },
                    onAction = { currentEntryId?.let { viewModel.analyzeCredibility(it, forceRefresh = true) } },
                    onDismissed = viewModel::clearScoreError
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = currentEntry != null,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD)),
                        initialOffsetY = { it / 2 }
                    ) + fadeIn(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT)),
                        targetOffsetY = { it / 2 }
                    ) + fadeOut(
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                    )
                ) {
                    currentEntry?.let { entry ->
                        val ttsContent = viewModel.getContentForEntry(entry.id)
                        val contentLoadFinished = entry.id in uiState.content ||
                            entry.id in uiState.partialContentIds
                        val ttsContentState = remember(entry.id, ttsContent, contentLoadFinished) {
                            articleTtsContentState(
                                content = ttsContent,
                                contentLoadFinished = contentLoadFinished
                            )
                        }
                        ArticleBottomActionBar(
                            modifier = Modifier.onSizeChanged { size ->
                                if (bottomActionBarHeightPx != size.height) {
                                    bottomActionBarHeightPx = size.height
                                }
                            },
                            state = ttsState,
                            temporaryModel = temporaryTtsModel,
                            configuredModel = configuredTtsModel,
                            modelStatuses = ttsModelStatuses,
                            onTemporaryModelChange = { model ->
                                if (ttsState.articleId != null) ttsController.stop()
                                temporaryTtsModel = model
                            },
                            entryUrl = entry.url.takeIf(String::isNotBlank),
                            isOnline = uiState.isOnline,
                            canUsePaywallBypass = entry.url.isNotBlank() &&
                                !paywallBypassService.isPaywallBypassUrl(entry.url),
                            onToggleSpeech = {
                                if (ttsState.articleId != null) {
                                    ttsController.stop()
                                } else if (ttsContentState == ArticleTtsContentState.AVAILABLE) {
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
                            },
                            onPauseSpeech = ttsController::pause,
                            onResumeSpeech = ttsController::resume,
                            onOpenInChrome = {
                                openChromeCustomTab(navController.context, cleanUrl(entry.url))
                            },
                            onBypassPaywall = {
                                if (entry.url.isNotBlank()) {
                                    val bypassMethod = preferencesManager.getPaywallBypassMethod()
                                    val bypassUrl = paywallBypassService.getBypassUrl(entry.url, bypassMethod)
                                    openChromeCustomTab(navController.context, bypassUrl)
                                }
                            },
                            contentState = ttsContentState,
                            onRetryContent = { viewModel.retryContent(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleContentErrorBanner(
    message: String,
    onRetry: () -> Unit,
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
        val result = hostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Long
        )
        onDismissed()
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
