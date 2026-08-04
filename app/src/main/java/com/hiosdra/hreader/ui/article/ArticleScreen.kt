package com.hiosdra.hreader.ui.article

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.hiosdra.hreader.data.model.CredibilityLevel
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.OfflinePage
import com.hiosdra.hreader.data.model.isRead
import com.hiosdra.hreader.data.paywall.PaywallBypassService
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.tts.ArticleTtsController
import com.hiosdra.hreader.data.tts.ArticleTtsState
import com.hiosdra.hreader.navigation.openChromeCustomTab
import com.hiosdra.hreader.ui.components.OfflineAwareImage
import com.hiosdra.hreader.ui.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.ui.theme.LocalCredibilityColors
import com.hiosdra.hreader.util.cleanUrl
import com.hiosdra.hreader.util.removeDuplicateArticleTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val MIN_ARTICLE_TEXT_SCALE = 0.85f
private const val MAX_ARTICLE_TEXT_SCALE = 1.35f
private const val ARTICLE_TEXT_SCALE_STEP = 0.1f
private const val FEED_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.72f
private const val WEB_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.85f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    feedId: Long?,
    startArticleId: Long,
    starredOnly: Boolean = false,
    includeRead: Boolean = false,
    sessionStartMillis: Long = 0L,
    viewModel: ArticleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { uiState.entries.size }
    var isWebViewMode by remember { mutableStateOf(false) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    // The pager opens on page 0 and only then jumps to the article being read, so
    // neither read state nor the reader's position may be touched before it lands.
    var pagerPositioned by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val preferencesManager: PreferencesManager = koinInject()
    val paywallBypassService: PaywallBypassService = koinInject()
    val ttsController: ArticleTtsController = koinInject()
    val ttsState by ttsController.state.collectAsState()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = currentEntry != null,
                enter = slideInVertically(
                    animationSpec = tween(180),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    animationSpec = tween(120),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(animationSpec = tween(120))
            ) {
                currentEntry?.let { entry ->
                    ArticleBottomActionBar(
                        state = ttsState,
                        entryUrl = entry.url.takeIf(String::isNotBlank),
                        isOnline = uiState.isOnline,
                        canUsePaywallBypass = entry.url.isNotBlank() &&
                            !paywallBypassService.isPaywallBypassUrl(entry.url),
                        onToggleSpeech = {
                            if (ttsState.articleId != null) {
                                ttsController.stop()
                            } else {
                                requestNotificationPermission {
                                    ttsController.play(
                                        articleId = entry.id,
                                        title = entry.title,
                                        html = removeDuplicateArticleTitle(
                                            viewModel.getContentForEntry(entry.id).orEmpty(),
                                            entry.title
                                        )
                                    )
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
                        }
                    )
                }
            }
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
                        viewModel.updateReadStatus(uiState.currentIndex, !entry.isRead)
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "",
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
                    localImagePaths = uiState.localImagePaths,
                    isOnline = uiState.isOnline,
                    aiOverviews = uiState.aiOverviews,
                    generatingOverviewIds = uiState.generatingOverviewIds,
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
                message = error,
                actionLabel = "Retry".takeIf { currentEntryId != null },
                onAction = { currentEntryId?.let { viewModel.generateAiOverview(it) } },
                onDismissed = viewModel::clearOverviewError
            )
        }

        // What is missing offline, said out loud rather than left as an empty screen.
        uiState.contentError?.let { message ->
            RetryableSnackbar(
                hostState = snackbarHostState,
                message = message,
                actionLabel = null,
                onAction = {},
                onDismissed = viewModel::clearContentError
            )
        }

        uiState.scoreError?.let { error ->
            RetryableSnackbar(
                hostState = snackbarHostState,
                message = error,
                actionLabel = "Retry".takeIf { currentEntryId != null },
                onAction = { currentEntryId?.let { viewModel.analyzeCredibility(it, forceRefresh = true) } },
                onDismissed = viewModel::clearScoreError
            )
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArticlePager(
    entries: List<Entry>,
    pagerState: PagerState,
    isWebViewMode: Boolean,
    textScale: Float,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    getContentForEntry: (Long) -> String?,
    getLeadImageForEntry: (Long) -> String?,
    getOfflinePageForEntry: (Long) -> OfflinePage?,
    localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
    isOnline: Boolean = true,
    aiOverviews: Map<Long, String> = emptyMap(),
    generatingOverviewIds: Set<Long> = emptySet(),
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    analyzingCredibilityIds: Set<Long> = emptySet(),
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapPositionalThreshold = if (isWebViewMode) {
                    WEB_PAGER_SNAP_POSITIONAL_THRESHOLD
                } else {
                    FEED_PAGER_SNAP_POSITIONAL_THRESHOLD
                }
            )
        ) { page ->
            val entry = entries.getOrNull(page) ?: return@HorizontalPager
            key(entry.id) {
                val offlinePage = getOfflinePageForEntry(entry.id)
                if (isWebViewMode && (isOnline || offlinePage != null)) {
                    if (!isOnline && offlinePage != null) {
                        OfflinePageWebView(
                            page = offlinePage,
                            onLinkClick = { url ->
                                copyTextToClipboard(context, "Link", url)
                                Toast.makeText(context, "Offline — link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        )
                    } else {
                        val loadedUrl = remember { mutableStateOf<String?>(null) }
                        val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
                        var savedScrollY by rememberSaveable(entry.id) { mutableStateOf(0) }
                        AndroidView(
                            factory = { context ->
                                ReaderWebView(context).apply {
                                    protectVerticalScrollFromPager = true
                                    settings.javaScriptEnabled = false
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val readerView = view as? ReaderWebView ?: return
                                            readerView.postIfActive { readerView.scrollTo(0, savedScrollY) }
                                        }
                                    }
                                    setOnScrollChangeListener { _, _, scrollY, _, _ -> savedScrollY = scrollY }
                                }
                            },
                            update = { webView ->
                                webView.settings.blockNetworkLoads = !isOnline
                                if (loadedWebView.value !== webView || loadedUrl.value != entry.url) {
                                    loadedWebView.value = webView
                                    loadedUrl.value = entry.url
                                    webView.loadUrl(entry.url)
                                    webView.postIfActive { webView.scrollTo(0, savedScrollY) }
                                }
                            },
                            onRelease = { webView -> webView.releaseResources() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        )
                    }
                } else {
                    ArticleContent(
                        entry = entry,
                        mainImageUrl = getLeadImageForEntry(entry.id),
                        textScale = textScale,
                        modifier = Modifier.padding(paddingValues),
                        articleContent = getContentForEntry(entry.id) ?: "No content available",
                        localImagePaths = localImagePaths[entry.id].orEmpty(),
                        isOnline = isOnline,
                        aiOverview = aiOverviews[entry.id],
                        isGeneratingOverview = generatingOverviewIds.contains(entry.id),
                        onAiOverview = onAiOverview,
                        credibilityEnabled = credibilityEnabled,
                        credibilityReport = credibilityReports[entry.id],
                        isAnalyzingCredibility = analyzingCredibilityIds.contains(entry.id),
                        onAnalyzeCredibility = onAnalyzeCredibility
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleTopBar(
    entryUrl: String?,
    feedTitle: String?,
    listPosition: Int,
    listSize: Int,
    isWebViewMode: Boolean,
    canUseWebView: Boolean,
    isStarred: Boolean,
    isRead: Boolean,
    textScale: Float,
    onDecreaseTextScale: () -> Unit,
    onResetTextScale: () -> Unit,
    onIncreaseTextScale: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleRead: () -> Unit,
    onBack: () -> Unit,
    onToggleWebView: () -> Unit,
    onShare: () -> Unit
) {
    TopAppBar(
        title = {
            // One line, cut short if it has to be. A feed named "Subiektywnie o finansach — Maciej
            // Samcik" wrapped to four of them, which grew the bar over the status bar above it and
            // the article below.
            Column {
                Text(
                    text = feedTitle ?: "hReader",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (listPosition in 1..listSize) {
                    Text(
                        text = "$listPosition / $listSize",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            val overflowExpanded = remember { mutableStateOf(false) }
            if (entryUrl != null) {
                FeedWebToggle(
                    isWebViewMode = isWebViewMode,
                    canUseWebView = canUseWebView,
                    onToggleWebView = onToggleWebView
                )
                ReadStatusButton(isRead = isRead, onToggleRead = onToggleRead)
            }
            // Outside the branch above: an entry that carries no address can still be starred, and
            // that is the one action here which is about the article rather than about its page.
            // The menu sits inside a box around its own button, or it anchors to a zero-width slot
            // after it and opens adrift of the edge it belongs to.
            Box {
                IconButton(onClick = { overflowExpanded.value = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded.value,
                    onDismissRequest = { overflowExpanded.value = false },
                    // The same treatment the list's menu gets, so the two do not read as two
                    // different components. Clip first: a background painted before it keeps
                    // square corners.
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isStarred) "Remove star" else "Star article") },
                        onClick = {
                            overflowExpanded.value = false
                            onToggleStar()
                        },
                        leadingIcon = {
                            // Tint rather than a second glyph: the outlined star is in the
                            // extended icon set. The label carries the state either way.
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (isStarred) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    )
                    if (entryUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                overflowExpanded.value = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    if (listSize > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Decrease text size") },
                            onClick = {
                                overflowExpanded.value = false
                                onDecreaseTextScale()
                            },
                            enabled = textScale > MIN_ARTICLE_TEXT_SCALE,
                            leadingIcon = { Text("A−") }
                        )
                        DropdownMenuItem(
                            text = { Text("Text size: ${(textScale * 100).roundToInt()}%") },
                            onClick = {
                                overflowExpanded.value = false
                                onResetTextScale()
                            },
                            leadingIcon = { Text("A") }
                        )
                        DropdownMenuItem(
                            text = { Text("Increase text size") },
                            onClick = {
                                overflowExpanded.value = false
                                onIncreaseTextScale()
                            },
                            enabled = textScale < MAX_ARTICLE_TEXT_SCALE,
                            leadingIcon = { Text("A+") }
                        )
                    }
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ReadStatusButton(
    isRead: Boolean,
    onToggleRead: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue = if (isRead) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(160),
        label = "read status color"
    )

    IconButton(
        onClick = onToggleRead,
        modifier = Modifier.semantics {
            contentDescription = readStatusActionLabel(isRead)
            stateDescription = if (isRead) "Read" else "Unread"
        }
    ) {
        AnimatedContent(
            targetState = isRead,
            transitionSpec = {
                (fadeIn(animationSpec = tween(120)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(120))) togetherWith
                    (fadeOut(animationSpec = tween(80)) +
                        scaleOut(targetScale = 0.8f, animationSpec = tween(80)))
            },
            label = "read status icon"
        ) { read ->
            Icon(
                imageVector = if (read) Icons.Filled.CheckCircle else Icons.Filled.Done,
                contentDescription = null,
                tint = iconTint
            )
        }
    }
}

@Composable
private fun FeedWebToggle(
    isWebViewMode: Boolean,
    canUseWebView: Boolean,
    onToggleWebView: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            ReaderModeOption(
                label = "FEED",
                selected = !isWebViewMode,
                enabled = true,
                contentDescription = "Show feed content",
                onClick = { if (isWebViewMode) onToggleWebView() }
            )
            ReaderModeOption(
                label = "WEB",
                selected = isWebViewMode,
                enabled = canUseWebView,
                contentDescription = if (canUseWebView) "Show original web page" else "Web mode unavailable offline",
                onClick = { if (!isWebViewMode && canUseWebView) onToggleWebView() }
            )
        }
    }
}

@Composable
private fun ReaderModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ArticleBottomActionBar(
    state: ArticleTtsState,
    entryUrl: String?,
    isOnline: Boolean,
    canUsePaywallBypass: Boolean,
    onToggleSpeech: () -> Unit,
    onPauseSpeech: () -> Unit,
    onResumeSpeech: () -> Unit,
    onOpenInChrome: () -> Unit,
    onBypassPaywall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (state.articleId != null) {
                ArticleTtsDetails(
                    state = state,
                    onPause = onPauseSpeech,
                    onResume = onResumeSpeech
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleSpeech) {
                    Icon(
                        imageVector = if (state.articleId != null) {
                            Icons.Filled.Close
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.articleId != null) {
                            "Stop reading"
                        } else {
                            "Read aloud"
                        }
                    )
                }
                if (entryUrl != null) {
                    IconButton(onClick = onOpenInChrome, enabled = isOnline) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chrome_logo),
                            contentDescription = "Open original page in Chrome",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (canUsePaywallBypass) {
                        IconButton(onClick = onBypassPaywall, enabled = isOnline) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Open through paywall bypass",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleTtsDetails(
    state: ArticleTtsState,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.isPreparing) "Preparing voice…" else state.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.error ?: state.model?.displayName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        TextButton(onClick = if (state.isPaused) onResume else onPause) {
            Text(if (state.isPaused) "Resume" else "Pause")
        }
    }
}

@Composable
private fun ArticleContent(
    entry: Entry,
    mainImageUrl: String?,
    textScale: Float,
    modifier: Modifier = Modifier,
    articleContent: String,
    localImagePaths: Map<String, String> = emptyMap(),
    isOnline: Boolean = true,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    val locale = LocalLocale.current.platformLocale
    val dateText = remember(entry.publishedAt, locale) { formatArticleDate(entry.publishedAt, locale) }
    val readableArticleContent = remember(articleContent, entry.title) {
        removeDuplicateArticleTitle(articleContent, entry.title)
    }
    val articleScrollState = rememberSaveable(entry.id, saver = ScrollState.Saver) { ScrollState(0) }
    var webContentHeightPx by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    var imageActionsUrl by remember { mutableStateOf<String?>(null) }
    var imageShareUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val webViewHeight = with(LocalDensity.current) { webContentHeightPx.toDp() }
    val scrollProgress = if (articleScrollState.maxValue > 0) {
        articleScrollState.value.toFloat() / articleScrollState.maxValue
    } else {
        0f
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(articleScrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(top = 12.dp)
                ) {
                    if (entry.feed.title.isNotBlank()) {
                        Text(
                            text = entry.feed.title.uppercase(locale),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            lineHeight = 34.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (scrollProgress in 0.02f..0.98f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scrollProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ArticleMeta(
                        author = entry.author,
                        dateText = dateText,
                        readingTimeMinutes = entry.readingTime,
                        entryId = entry.id,
                        isOnline = isOnline,
                        aiOverview = aiOverview,
                        isGeneratingOverview = isGeneratingOverview,
                        onAiOverviewClick = if (onAiOverview != null) { { onAiOverview(entry.id) } } else null,
                        credibilityEnabled = credibilityEnabled,
                        credibilityReport = credibilityReport,
                        isAnalyzingCredibility = isAnalyzingCredibility,
                        onAnalyzeCredibility = if (onAnalyzeCredibility != null) {
                            { force -> onAnalyzeCredibility(entry.id, force) }
                        } else null
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()

                    if (mainImageUrl != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        OfflineAwareImage(
                            entryId = entry.id,
                            imageUrl = mainImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { zoomImageUrl = mainImageUrl },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    ArticleWebView(
                        articleContent = readableArticleContent,
                        baseUrl = entry.url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(webViewHeight.coerceAtLeast(240.dp)),
                        allowNetworkLoads = isOnline,
                        localImagePaths = localImagePaths,
                        textScale = textScale,
                        scrollEnabled = false,
                        onContentHeightChanged = { height -> webContentHeightPx = height },
                        onLinkClick = { url ->
                            // A custom tab offline is a browser error page, and the link is gone
                            // by the time the reader is back in range.
                            if (isOnline) {
                                openChromeCustomTab(context, url)
                            } else {
                                copyTextToClipboard(context, "Link", url)
                                Toast.makeText(context, "Offline — link copied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImageLongClick = { url -> imageActionsUrl = url }
                    )
                }
            }
        }
    }
    val actionsUrl = imageActionsUrl
    if (actionsUrl != null) {
        ImageActionsDialog(
            imageUrl = actionsUrl,
            isOnline = isOnline,
            onDismiss = { imageActionsUrl = null },
            onView = {
                zoomImageUrl = actionsUrl
                imageActionsUrl = null
            },
            onCopy = {
                copyTextToClipboard(context, "Image URL", actionsUrl)
                imageActionsUrl = null
            },
            onDownload = {
                if (isOnline) {
                    enqueueImageDownload(context, actionsUrl)
                } else {
                    Toast.makeText(context, "Downloading needs a connection", Toast.LENGTH_SHORT).show()
                }
                imageActionsUrl = null
            },
            onShare = {
                if (isOnline) {
                    imageShareUrl = actionsUrl
                } else {
                    Toast.makeText(context, "Sharing needs a connection", Toast.LENGTH_SHORT).show()
                }
                imageActionsUrl = null
            }
        )
    }
    val shareTarget = imageShareUrl
    if (shareTarget != null) {
        LaunchedEffect(shareTarget) {
            Toast.makeText(context, "Preparing image...", Toast.LENGTH_SHORT).show()
            val shared = shareImageFile(context, entry.title, shareTarget)
            if (!shared) Toast.makeText(context, "Image share failed", Toast.LENGTH_SHORT).show()
            imageShareUrl = null
        }
    }
    val zoomUrl = zoomImageUrl
    if (zoomUrl != null) {
        Dialog(onDismissRequest = { zoomImageUrl = null }) {
            ZoomableImage(entryId = entry.id, url = zoomUrl) { zoomImageUrl = null }
        }
    }
}

@Composable
private fun ArticleMeta(
    author: String?,
    dateText: String,
    readingTimeMinutes: Int?,
    entryId: Long? = null,
    isOnline: Boolean = true,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    onAiOverviewClick: (() -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Boolean) -> Unit)? = null
) {
    val isAiExpanded = rememberSaveable { mutableStateOf(false) }
    val isCredibilityExpanded = rememberSaveable { mutableStateOf(false) }

    val aiOverviewClick = if (entryId != null) onAiOverviewClick else null
    val analyzeCredibility = if (entryId != null && credibilityEnabled) onAnalyzeCredibility else null

    val metadata = buildList {
        if (!author.isNullOrBlank()) add(author)
        add(dateText)
        if (readingTimeMinutes != null && readingTimeMinutes > 0) add("$readingTimeMinutes min read")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = metadata.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (aiOverviewClick != null || analyzeCredibility != null) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (aiOverviewClick != null) {
                androidx.compose.material3.AssistChip(
                    onClick = {
                        if (aiOverview == null) {
                            isAiExpanded.value = true
                            aiOverviewClick()
                        } else {
                            isAiExpanded.value = !isAiExpanded.value
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "AI Overview",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val chipText = when {
                                aiOverview == null && isGeneratingOverview -> "Generating..."
                                aiOverview == null -> "AI Overview"
                                isAiExpanded.value -> "Hide Overview"
                                else -> "Show Overview"
                            }
                            Text(chipText)
                        }
                    },
                    // A cached overview still expands offline; generating a new one cannot.
                    enabled = !(aiOverview == null && isGeneratingOverview) && (aiOverview != null || isOnline),
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = if (aiOverview != null || isGeneratingOverview)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            if (analyzeCredibility != null) {
                CredibilityChip(
                    report = credibilityReport,
                    isAnalyzing = isAnalyzingCredibility,
                    enabled = credibilityReport != null || isOnline,
                    onClick = {
                        if (credibilityReport == null) {
                            isCredibilityExpanded.value = true
                            analyzeCredibility(false)
                        } else {
                            isCredibilityExpanded.value = !isCredibilityExpanded.value
                        }
                    }
                )
            }
        }

        // AI Overview Content with Animation
        if (entryId != null && (aiOverview != null || isGeneratingOverview) && isAiExpanded.value) {
            AnimatedVisibility(
                visible = isAiExpanded.value,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "AI Overview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isGeneratingOverview) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generating overview...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = aiOverview ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (entryId != null && credibilityEnabled && isCredibilityExpanded.value &&
            (credibilityReport != null || isAnalyzingCredibility)
        ) {
            AnimatedVisibility(
                visible = isCredibilityExpanded.value,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                CredibilityCard(
                    report = credibilityReport,
                    isAnalyzing = isAnalyzingCredibility,
                    onReanalyze = { onAnalyzeCredibility?.invoke(true) }
                )
            }
        }
    }
}

@Composable
private fun CredibilityChip(
    report: CredibilityReport?,
    isAnalyzing: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = credibilityAccent(report)
    val container = report?.let { credibilityContainerColor(it.level) }
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                val chipText = when {
                    isAnalyzing -> "Analyzing..."
                    report == null -> "Check credibility"
                    else -> credibilityLevelLabel(report.level)
                }
                Text(chipText)
            }
        },
        enabled = !isAnalyzing && enabled,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = when {
                container != null -> container
                isAnalyzing -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    )
}

@Composable
private fun CredibilityCard(
    report: CredibilityReport?,
    isAnalyzing: Boolean,
    onReanalyze: () -> Unit
) {
    val accent = credibilityAccent(report) ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (report == null) "Credibility" else credibilityLevelLabel(report.level),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAnalyzing || report == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Analyzing the article...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            if (report.summary.isNotBlank()) {
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (report.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CredibilityBulletList(
                    title = "What the model saw",
                    items = report.reasons,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (report.redFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CredibilityBulletList(
                    title = "Red flags",
                    items = report.redFlags,
                    color = LocalCredibilityColors.current.low
                )
            }

            if (report.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                report.factors.forEach { factor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = factor.name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f)
                        )
                        LinearProgressIndicator(
                            progress = { factor.score },
                            color = credibilityColor(CredibilityLevel.fromScore(factor.score)),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = credibilityDisclaimer(report),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = onReanalyze,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Re-analyze")
            }
        }
    }
}

@Composable
private fun CredibilityBulletList(title: String, items: List<String>, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    items.forEach { item ->
        Row(modifier = Modifier.padding(vertical = 1.dp)) {
            Text(text = "• ", style = MaterialTheme.typography.bodySmall, color = color)
            Text(text = item, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

private fun credibilityLevelLabel(level: CredibilityLevel): String = when (level) {
    CredibilityLevel.HIGH -> "Credibility: strong signals"
    CredibilityLevel.MIXED -> "Credibility: mixed signals"
    CredibilityLevel.LOW -> "Credibility: weak signals"
}

@Composable
private fun credibilityColor(level: CredibilityLevel): Color {
    val credibility = LocalCredibilityColors.current
    return when (level) {
        CredibilityLevel.HIGH -> credibility.high
        CredibilityLevel.MIXED -> credibility.mixed
        CredibilityLevel.LOW -> credibility.low
    }
}

@Composable
private fun credibilityContainerColor(level: CredibilityLevel): Color {
    val credibility = LocalCredibilityColors.current
    return when (level) {
        CredibilityLevel.HIGH -> credibility.highContainer
        CredibilityLevel.MIXED -> credibility.mixedContainer
        CredibilityLevel.LOW -> credibility.lowContainer
    }
}

@Composable
private fun credibilityAccent(report: CredibilityReport?): Color? =
    report?.let { credibilityColor(it.level) }

private fun credibilityDisclaimer(report: CredibilityReport): String = buildString {
    append("AI estimate from the article text only — not a fact check. ")
    append(
        when (report.confidence) {
            CredibilityConfidence.HIGH -> "Model confidence: high. "
            CredibilityConfidence.MEDIUM -> "Model confidence: medium. "
            CredibilityConfidence.LOW -> "Model confidence: low. "
        }
    )
    if (report.contentTruncated) append("Long article, only the first part was analyzed. ")
    append("Analyzed ${formatTimestamp(report.analyzedAt)} with ${report.modelId}.")
}

private fun formatTimestamp(instant: Instant): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)

private fun formatArticleDate(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageActionsDialog(
    imageUrl: String,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = imageUrl,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("View") },
                modifier = Modifier.clickable(onClick = onView)
            )
            ListItem(
                headlineContent = { Text("Copy URL") },
                modifier = Modifier.clickable(onClick = onCopy)
            )
            ListItem(
                headlineContent = { Text("Download") },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onDownload)
            )
            ListItem(
                headlineContent = { Text("Share image") },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onShare)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun enqueueImageDownload(context: Context, url: String) {
    val started = runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(url.toUri())
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeImageFileName(url))
        dm.enqueue(request)
    }.isSuccess
    // Both outcomes are announced. The toast used to sit inside the runCatching, so an address
    // DownloadManager would not take left the button looking like it had done nothing at all.
    val message = if (started) "Downloading" else "Could not start the download"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * A file name derived from an address in the article body, which is third-party content.
 * `Uri.lastPathSegment` is percent-decoded, so a crafted address can carry separators and walk out
 * of the directory it is written to; everything outside a conservative set is replaced.
 */
private fun safeImageFileName(url: String, extension: String = ""): String {
    val segment = runCatching { url.toUri().lastPathSegment }.getOrNull().orEmpty()
    val sanitized = segment
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
        .take(80)
    val base = sanitized.ifBlank { "image_${System.currentTimeMillis()}" }
    return if (extension.isEmpty() || base.endsWith(extension, ignoreCase = true)) base else base + extension
}

/**
 * Its own client rather than the shared one: this fetches an arbitrary address from the article
 * body and must not carry the backend interceptors. Timeouts are set explicitly — the default
 * builder has none for the whole call, and a stalled image download would hang the share sheet.
 */
private val imageShareClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .build()

/** A shared image larger than this is a photographer's export, not something to hold in memory. */
private const val MAX_SHARED_IMAGE_BYTES = 16L * 1024 * 1024

/** How many prepared images are kept. Each share used to leave one behind for good. */
private const val MAX_SHARED_IMAGE_FILES = 8

private suspend fun shareImageFile(context: Context, title: String?, url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder().url(url).build()
        imageShareClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body
            val contentType = body.contentType()?.toString() ?: "image/jpeg"
            // Read with a ceiling rather than body.bytes(), which will happily pull a 200 MB
            // response into memory before anyone can object.
            val bytes = body.byteStream().readAtMost(MAX_SHARED_IMAGE_BYTES) ?: return@withContext false

            val extension = when {
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                contentType.contains("gif") -> ".gif"
                contentType.contains("svg") -> ".svg"
                contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                else -> ".img"
            }
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            pruneSharedImages(dir)
            val outFile = File(dir, safeImageFileName(url, extension))
            FileOutputStream(outFile).use { it.write(bytes) }

            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = contentType
                    putExtra(Intent.EXTRA_SUBJECT, title ?: "Image")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
            true
        }
    } catch (_: Exception) {
        false
    }
}

/** The whole stream, or null once it goes past [limit]. */
private fun InputStream.readAtMost(limit: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    use { input ->
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
    }
    return buffer.toByteArray()
}

/**
 * Keeps the newest few prepared images. The receiving app reads the file after this function has
 * returned, so the one just written cannot be deleted on the spot — it is the next share that
 * clears it.
 */
private fun pruneSharedImages(dir: File) {
    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
    files.drop(MAX_SHARED_IMAGE_FILES - 1).forEach { it.delete() }
}
