package com.hiosdra.hreader.presentation.article

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.presentation.components.OfflineAwareImage
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import com.hiosdra.hreader.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.FlowPreview

private const val OVERSIZED_ARTICLE_HEADER_RESIZE_DEBOUNCE_MS = 150L
private const val ARTICLE_SCROLL_END_TOLERANCE_PX = 8

@OptIn(FlowPreview::class)
@Composable
internal fun ArticleContent(
    entry: Entry,
    mainImageUrl: String?,
    textScale: Float,
    modifier: Modifier = Modifier,
    articleContent: String,
    contentLoaded: Boolean,
    contentState: ArticleContentLoadState = ArticleContentLoadState.FULL,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit,
    articleImageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
    imageSharer: ArticleImageSharer,
    imageDownloader: ArticleImageDownloader,
    readerPreferences: ReaderPreferences,
    localImagePaths: Map<String, String> = emptyMap(),
    isOnline: Boolean = true,
    aiOverview: String? = null,
    aiProvider: AiProvider = AiProvider.OPENROUTER,
    isGeneratingOverview: Boolean = false,
    aiOverviewProgress: ArticleAiProgress? = null,
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null,
    defaultPaywallBypassMethod: PaywallBypassMethod = PaywallBypassMethod.SMRY_AI,
    canUsePaywallBypass: (String) -> Boolean = { false },
    onOpenInChrome: (String) -> Unit = {},
    onBypassPaywall: (String, PaywallBypassMethod) -> Unit = { _, _ -> }
) {
    val locale = LocalLocale.current.platformLocale
    val feedTitle = entry.feed.title.ifBlank { stringResource(R.string.article_unknown_feed) }
    val dateText = remember(entry.publishedAt, locale) { formatArticleDate(entry.publishedAt, locale) }
    val readableArticleContent = articleContent
    val contentFingerprint = readableArticleContent.hashCode()
    val articleScrollState = rememberSaveable(entry.id, saver = ScrollState.Saver) { ScrollState(0) }
    var restoredContentPositionKey by rememberSaveable(entry.id) { mutableStateOf<Int?>(null) }
    var readingCompletionReported by rememberSaveable(entry.id) { mutableStateOf(false) }
    var webContentHeightPx by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableIntStateOf(0)
    }
    var measuredWebContentTopInsetPx by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableIntStateOf(0)
    }
    var webContentHeightSettled by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableStateOf(false)
    }
    var webViewRestoreScrollY by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableIntStateOf(0)
    }
    var webViewScrollProgress by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableFloatStateOf(0f)
    }
    var webViewScrollY by rememberSaveable(entry.id, contentFingerprint, contentState) {
        mutableIntStateOf(0)
    }
    var articleViewportHeightPx by remember(entry.id) { mutableIntStateOf(0) }
    var articleHeaderHeightPx by remember(entry.id) { mutableIntStateOf(0) }
    var webContentTopInsetPx by remember(entry.id, contentFingerprint, contentState) { mutableIntStateOf(0) }
    var keepArticleScrollAtEnd by remember(entry.id, contentFingerprint, contentState) {
        mutableStateOf(false)
    }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    var imageActionsUrl by remember { mutableStateOf<String?>(null) }
    var imageShareUrl by remember { mutableStateOf<String?>(null) }
    var imageDownloadUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val articleLinkLabel = stringResource(R.string.article_link)
    val offlineLinkCopiedMessage = stringResource(R.string.article_offline_link_copied)
    val imageUrlLabel = stringResource(R.string.article_image_url)
    val downloadingRequiresConnectionMessage = stringResource(R.string.article_downloading_requires_connection)
    val sharingRequiresConnectionMessage = stringResource(R.string.article_sharing_requires_connection)
    val preparingImageMessage = stringResource(R.string.article_preparing_image)
    val imageSharingFailedMessage = stringResource(R.string.article_image_sharing_failed)
    val imageDownloadedMessage = stringResource(R.string.article_downloading)
    val imageDownloadFailedMessage = stringResource(R.string.article_download_failed)
    val loadingArticlesDescription = stringResource(R.string.loading_articles)
    val density = LocalDensity.current
    val minimumWebViewHeightPx = with(density) { 240.dp.roundToPx() }
    val articleBodyHeightPx = (webContentHeightPx - measuredWebContentTopInsetPx).coerceAtLeast(0)
    val safeWebContentHeightPx = safeArticleWebViewHeightPx(articleBodyHeightPx)
    val measuredOversizedArticle = articleWebViewNeedsInternalScroll(articleBodyHeightPx)
    var oversizedArticleDetected by remember(entry.id, contentFingerprint, contentState) {
        mutableStateOf(false)
    }
    LaunchedEffect(measuredOversizedArticle) {
        if (measuredOversizedArticle) oversizedArticleDetected = true
    }
    val webViewNeedsInternalScroll = measuredOversizedArticle || oversizedArticleDetected
    val webViewHeightPx = if (webViewNeedsInternalScroll) {
        articleViewportHeightPx.coerceAtLeast(minimumWebViewHeightPx)
    } else {
        safeWebContentHeightPx.coerceAtLeast(minimumWebViewHeightPx)
    }
    val webViewHeight = with(density) { webViewHeightPx.toDp() }
    val webViewMaxScrollPx = (webContentHeightPx - webViewHeightPx).coerceAtLeast(0)
    LaunchedEffect(contentState, contentFingerprint) {
        if (contentState != ArticleContentLoadState.LOADING) return@LaunchedEffect
        articleScrollState.scrollTo(0)
        webViewRestoreScrollY = 0
        webViewScrollProgress = 0f
        webViewScrollY = 0
        keepArticleScrollAtEnd = false
    }
    LaunchedEffect(keepArticleScrollAtEnd, webContentHeightPx, webViewHeightPx, webViewNeedsInternalScroll) {
        if (!keepArticleScrollAtEnd || webViewNeedsInternalScroll) return@LaunchedEffect
        withFrameNanos { }
        articleScrollState.scrollTo(articleScrollState.maxValue)
        keepArticleScrollAtEnd = false
    }
    LaunchedEffect(webViewNeedsInternalScroll, articleHeaderHeightPx) {
        if (!webViewNeedsInternalScroll || articleHeaderHeightPx <= 0) return@LaunchedEffect
        if (webContentTopInsetPx != 0) delay(OVERSIZED_ARTICLE_HEADER_RESIZE_DEBOUNCE_MS)
        webContentTopInsetPx = articleHeaderHeightPx
    }
    val oversizedContentLayoutReady = !webViewNeedsInternalScroll ||
        webContentTopInsetPx > 0 && measuredWebContentTopInsetPx == webContentTopInsetPx
    val webViewScrollController = remember(entry.id) { ArticleWebViewScrollController() }
    val headerScrollableState = rememberScrollableState { deltaY ->
        webViewScrollController.consumeComposeScrollDelta(deltaY)
    }
    val scrollbarMetrics by remember(
        articleScrollState,
        webViewNeedsInternalScroll,
        webViewMaxScrollPx
    ) {
        derivedStateOf {
            if (webViewNeedsInternalScroll) {
                verticalScrollbarMetrics(
                    viewportSizePx = articleViewportHeightPx,
                    contentSizePx = webContentHeightPx,
                    scrollOffsetPx = articleScrollOffset(webViewScrollProgress, webViewMaxScrollPx)
                )
            } else {
                val contentSizePx = (articleViewportHeightPx.toLong() + articleScrollState.maxValue)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                verticalScrollbarMetrics(
                    viewportSizePx = articleViewportHeightPx,
                    contentSizePx = contentSizePx,
                    scrollOffsetPx = articleScrollState.value
                )
            }
        }
    }
    val latestReadingPositionLoaded = rememberUpdatedState(readingPositionLoaded)
    val latestContentState = rememberUpdatedState(contentState)
    val latestWebContentHeightSettled = rememberUpdatedState(webContentHeightSettled)
    val latestOnReadingProgressChanged = rememberUpdatedState(onReadingProgressChanged)
    val latestOnReadingCompleted = rememberUpdatedState(onReadingCompleted)
    val contentPositionKey = (31 * contentFingerprint + contentState.ordinal) xor
        (if (webViewNeedsInternalScroll) Int.MIN_VALUE else 0)
    val articleContentLayoutReady = contentState != ArticleContentLoadState.LOADING &&
        webContentHeightPx > 0 && webContentHeightSettled
    val currentArticleProgress = {
        if (webViewNeedsInternalScroll) {
            articleScrollProgress(webViewScrollY, webViewMaxScrollPx) to (webViewMaxScrollPx > 0)
        } else {
            val maxValue = articleScrollState.maxValue
            articleScrollProgress(articleScrollState.value, maxValue) to (maxValue > 0)
        }
    }

    LaunchedEffect(
        entry.id,
        contentLoaded,
        readingPositionLoaded,
        savedReadingProgress,
        contentPositionKey,
        articleContentLayoutReady,
        webContentHeightSettled,
        oversizedContentLayoutReady
    ) {
        if (
            restoredContentPositionKey == contentPositionKey ||
            !contentLoaded ||
            !readingPositionLoaded ||
            !articleContentLayoutReady ||
            !oversizedContentLayoutReady
        ) {
            return@LaunchedEffect
        }
        val progress = savedReadingProgress
        if (progress == null) {
            restoredContentPositionKey = contentPositionKey
            return@LaunchedEffect
        }

        if (webViewNeedsInternalScroll) {
            webViewRestoreScrollY = articleScrollOffset(progress, webViewMaxScrollPx)
        } else {
            val maxValue = snapshotFlow { articleScrollState.maxValue }.first { it > 0 }
            articleScrollState.scrollTo(articleScrollOffset(progress, maxValue))
        }
        restoredContentPositionKey = contentPositionKey
    }

    LaunchedEffect(
        entry.id,
        contentState,
        readingPositionLoaded,
        webViewNeedsInternalScroll,
        webViewMaxScrollPx,
        webContentHeightSettled
    ) {
        if (
            !readingPositionLoaded ||
            contentState == ArticleContentLoadState.LOADING ||
            !webContentHeightSettled
        ) {
            return@LaunchedEffect
        }
        readingCompletionReported = false
        snapshotFlow { currentArticleProgress() }
            .filter { (_, ready) -> ready }
            .sample(READING_POSITION_SAMPLE_MILLIS)
            .collect { (progress, _) ->
                if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                    if (!readingCompletionReported) {
                        readingCompletionReported = true
                        latestOnReadingCompleted.value(entry.id)
                    }
                } else {
                    readingCompletionReported = false
                    latestOnReadingProgressChanged.value(entry.id, progress)
                }
            }
    }

    DisposableEffect(
        entry.id,
        contentState,
        webViewNeedsInternalScroll,
        webViewMaxScrollPx,
        webContentHeightSettled
    ) {
        onDispose {
            if (
                !latestReadingPositionLoaded.value ||
                latestContentState.value == ArticleContentLoadState.LOADING ||
                !latestWebContentHeightSettled.value
            ) {
                return@onDispose
            }
            val (progress, ready) = currentArticleProgress()
            if (!ready) return@onDispose
            if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                latestOnReadingCompleted.value(entry.id)
            } else {
                latestOnReadingProgressChanged.value(entry.id, progress)
            }
        }
    }
    val onWebContentHeightChanged: (Int, Int, Boolean) -> Unit = { height, topInset, settled ->
        val previousMax = articleScrollState.maxValue
        val wasAtEnd = !webViewNeedsInternalScroll &&
            previousMax > 0 &&
            articleScrollState.value >= previousMax - ARTICLE_SCROLL_END_TOLERANCE_PX
        if (wasAtEnd && height > webContentHeightPx) keepArticleScrollAtEnd = true
        webContentHeightPx = height
        measuredWebContentTopInsetPx = topInset
        webContentHeightSettled = settled
    }
    val articleHeader: @Composable (Modifier) -> Unit = { headerModifier ->
        Column(
            modifier = headerModifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(top = 12.dp)
        ) {
            Text(
                text = feedTitle.uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            ArticleMetadata(
                author = entry.author,
                dateText = dateText,
                readingTimeMinutes = entry.readingTime,
                isOnline = isOnline,
                aiProvider = aiProvider,
                aiOverview = aiOverview,
                isGeneratingOverview = isGeneratingOverview,
                aiOverviewProgress = aiOverviewProgress,
                onAiOverviewClick = if (onAiOverview != null) { { onAiOverview(entry.id) } } else null,
                credibilityEnabled = credibilityEnabled,
                credibilityReport = credibilityReport,
                isAnalyzingCredibility = isAnalyzingCredibility,
                onAnalyzeCredibility = if (onAnalyzeCredibility != null) {
                    { force -> onAnalyzeCredibility(entry.id, force) }
                } else null
            )
            if (entry.url.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ArticleSourceActions(
                    defaultPaywallBypassMethod = defaultPaywallBypassMethod,
                    isOnline = isOnline,
                    canUsePaywallBypass = canUsePaywallBypass(entry.url),
                    onOpenInChrome = { onOpenInChrome(entry.url) },
                    onBypassPaywall = { method -> onBypassPaywall(entry.url, method) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            if (mainImageUrl != null) {
                Spacer(modifier = Modifier.height(20.dp))
                OfflineAwareImage(
                    entryId = entry.id,
                    imageUrl = mainImageUrl,
                    contentDescription = null,
                    isOnline = isOnline,
                    articleImageLoader = articleImageLoader,
                    coilImageLoader = coilImageLoader,
                    remoteResourcePolicy = remoteResourcePolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { zoomImageUrl = mainImageUrl },
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    val onArticleLinkClick: (String) -> Unit = { url ->
        if (isOnline) {
            openChromeCustomTab(context, url)
        } else {
            copyTextToClipboard(context, articleLinkLabel, url)
            Toast.makeText(context, offlineLinkCopiedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { articleViewportHeightPx = it.height }
        ) {
            if (webViewNeedsInternalScroll) {
                if (webContentTopInsetPx > 0) {
                    ArticleWebView(
                        articleContent = readableArticleContent,
                        baseUrl = entry.url,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        allowNetworkLoads = isOnline,
                        localImagePaths = localImagePaths,
                        textScale = textScale,
                        scrollEnabled = true,
                        contentTopInsetPx = webContentTopInsetPx,
                        scrollController = webViewScrollController,
                        restoreScrollY = webViewRestoreScrollY,
                        onScrollYChanged = { scrollY -> webViewScrollY = scrollY },
                        onScrollProgress = { progress -> webViewScrollProgress = progress },
                        onContentHeightChanged = onWebContentHeightChanged,
                        onContentLoadStarted = { webContentHeightSettled = false },
                        onLinkClick = onArticleLinkClick,
                        onImageLongClick = { url -> imageActionsUrl = url },
                        readerPreferences = readerPreferences,
                        remoteResourcePolicy = remoteResourcePolicy
                    )
                }
                articleHeader(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight(unbounded = true)
                        .onSizeChanged { articleHeaderHeightPx = it.height }
                        .graphicsLayer {
                            translationY = -oversizedArticleHeaderScrollPx(
                                webViewScrollY,
                                articleHeaderHeightPx
                            ).toFloat()
                        }
                        .scrollable(
                            state = headerScrollableState,
                            orientation = Orientation.Vertical
                        )
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(articleScrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    articleHeader(
                        Modifier.onSizeChanged { articleHeaderHeightPx = it.height }
                    )
                    if (contentState == ArticleContentLoadState.LOADING) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = loadingArticlesDescription
                                }
                            )
                        }
                    } else {
                        ArticleWebView(
                            articleContent = readableArticleContent,
                            baseUrl = entry.url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .height(webViewHeight.coerceAtLeast(240.dp)),
                            allowNetworkLoads = isOnline,
                            localImagePaths = localImagePaths,
                            textScale = textScale,
                            scrollEnabled = false,
                            restoreScrollY = 0,
                            onScrollProgress = { progress -> webViewScrollProgress = progress },
                            onContentHeightChanged = onWebContentHeightChanged,
                            onContentLoadStarted = { webContentHeightSettled = false },
                            onLinkClick = onArticleLinkClick,
                            onImageLongClick = { url -> imageActionsUrl = url },
                            readerPreferences = readerPreferences,
                            remoteResourcePolicy = remoteResourcePolicy
                        )
                    }
                }
            }
            VerticalScrollbar(
                metrics = scrollbarMetrics,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            )
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
                copyTextToClipboard(context, imageUrlLabel, actionsUrl)
                imageActionsUrl = null
            },
            onDownload = {
                if (isOnline) {
                    imageDownloadUrl = actionsUrl
                } else {
                    Toast.makeText(context, downloadingRequiresConnectionMessage, Toast.LENGTH_SHORT).show()
                }
                imageActionsUrl = null
            },
            onShare = {
                if (isOnline) {
                    imageShareUrl = actionsUrl
                } else {
                    Toast.makeText(context, sharingRequiresConnectionMessage, Toast.LENGTH_SHORT).show()
                }
                imageActionsUrl = null
            }
        )
    }
    val downloadTarget = imageDownloadUrl
    if (downloadTarget != null) {
        LaunchedEffect(downloadTarget) {
            val downloaded = imageDownloader.download(downloadTarget)
            val message = if (downloaded) imageDownloadedMessage else imageDownloadFailedMessage
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            imageDownloadUrl = null
        }
    }
    val shareTarget = imageShareUrl
    if (shareTarget != null) {
        LaunchedEffect(shareTarget) {
            Toast.makeText(context, preparingImageMessage, Toast.LENGTH_SHORT).show()
            val shared = imageSharer.share(entry.title, shareTarget)
            if (!shared) Toast.makeText(context, imageSharingFailedMessage, Toast.LENGTH_SHORT).show()
            imageShareUrl = null
        }
    }
    val zoomUrl = zoomImageUrl
    if (zoomUrl != null) {
        Dialog(onDismissRequest = { zoomImageUrl = null }) {
            ZoomableImage(
                entryId = entry.id,
                url = zoomUrl,
                isOnline = isOnline,
                articleImageLoader = articleImageLoader,
                coilImageLoader = coilImageLoader,
                remoteResourcePolicy = remoteResourcePolicy
            ) { zoomImageUrl = null }
        }
    }
}

private fun formatArticleDate(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
