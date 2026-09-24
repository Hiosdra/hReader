package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val OVERSIZED_ARTICLE_HEADER_RESIZE_DEBOUNCE_MS = 150L
private const val ARTICLE_SCROLL_END_TOLERANCE_PX = 8

@Composable
internal fun ArticleContent(
    entry: Entry,
    textScale: Float,
    modifier: Modifier = Modifier,
    bindings: ArticlePagerBindings
) {
    val state = bindings.state
    val content = state.content
    val contentState = content.contentLoadState(entry.id)
    val articleContent = content.content[entry.id] ?: stringResource(R.string.article_no_content)
    val contentLoaded = entry.id in content.content.keys || contentState == ArticleContentLoadState.FALLBACK
    val contentProvenance = state.getContentProvenance(entry.id)
    val readingPositionLoaded = entry.id in content.readingProgress.loadedIds
    val savedReadingProgress = content.readingProgress.positions[entry.id]
    val localImagePaths = content.localImagePaths[entry.id].orEmpty()
    val isOnline = content.isOnline
    val onEffect = bindings.onEffect
    val articleImageLoader = bindings.articleImageLoader
    val coilImageLoader = bindings.coilImageLoader
    val remoteResourcePolicy = bindings.remoteResourcePolicy
    val readerPreferences = bindings.readerPreferences
    val onReadingProgressChanged = bindings.onReadingProgressChanged
    val onReadingCompleted = bindings.onReadingCompleted
    val onRetryContent = { bindings.onRetryContent(entry.id) }
    val locale = LocalLocale.current.platformLocale
    val feedTitle = entry.feed.title.ifBlank { stringResource(R.string.article_unknown_feed) }
    val dateText = remember(entry.publishedAt, locale) { formatArticleDate(entry.publishedAt, locale) }
    val readableArticleContent = articleContent
    val leadImageUrl = content.leadImages[entry.id]?.takeUnless { imageUrl ->
        readableArticleContent.contains(imageUrl)
    }
    val contentFingerprint = readableArticleContent.hashCode()
    val articleScrollState = rememberSaveable(entry.id, saver = ScrollState.Saver) { ScrollState(0) }
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
    val articleLinkLabel = stringResource(R.string.article_link)
    val offlineLinkCopiedMessage = stringResource(R.string.article_offline_link_copied)
    val imageUrlLabel = stringResource(R.string.article_image_url)
    val copiedMessage = stringResource(R.string.article_copied)
    val downloadingRequiresConnectionMessage = stringResource(R.string.article_downloading_requires_connection)
    val sharingRequiresConnectionMessage = stringResource(R.string.article_sharing_requires_connection)
    val preparingImageMessage = stringResource(R.string.article_preparing_image)
    val imageSharingFailedMessage = stringResource(R.string.article_image_sharing_failed)
    val imageDownloadedMessage = stringResource(R.string.article_image_downloaded)
    val imageDownloadFailedMessage = stringResource(R.string.article_download_failed)
    val retryActionLabel = stringResource(R.string.action_retry)
    val loadingArticlesDescription = stringResource(R.string.loading_articles)

    fun dispatchImageDownload(url: String) {
        onEffect(
            ArticleRouteEffect.DownloadImage(
                url = url,
                resultMessage = imageDownloadedMessage,
                failureMessage = imageDownloadFailedMessage,
                retryActionLabel = retryActionLabel,
                onRetry = { dispatchImageDownload(url) }
            )
        )
    }

    fun dispatchImageShare(url: String) {
        onEffect(
            ArticleRouteEffect.ShareImage(
                title = entry.title,
                url = url,
                preparingMessage = preparingImageMessage,
                failureMessage = imageSharingFailedMessage,
                retryActionLabel = retryActionLabel,
                onRetry = { dispatchImageShare(url) }
            )
        )
    }

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
    val contentPositionKey = (31 * contentFingerprint + contentState.ordinal) xor
        (if (webViewNeedsInternalScroll) Int.MIN_VALUE else 0)
    val articleContentLayoutReady = contentState != ArticleContentLoadState.LOADING &&
        webContentHeightPx > 0 && webContentHeightSettled
    ArticleReadingPositionTracker(
        entryId = entry.id,
        contentKey = contentPositionKey,
        effectKey = listOf(
            contentPositionKey,
            webContentHeightPx,
            webViewMaxScrollPx,
            webContentHeightSettled,
            oversizedContentLayoutReady
        ),
        readingPositionLoaded = readingPositionLoaded,
        savedReadingProgress = savedReadingProgress,
        positionReady = contentLoaded && articleContentLayoutReady && oversizedContentLayoutReady,
        currentProgress = {
            if (webViewNeedsInternalScroll) {
                articleScrollProgress(webViewScrollY, webViewMaxScrollPx) to (webViewMaxScrollPx > 0)
            } else {
                val maxValue = articleScrollState.maxValue
                articleScrollProgress(articleScrollState.value, maxValue) to (maxValue > 0)
            }
        },
        restorePosition = { progress ->
            if (webViewNeedsInternalScroll) {
                webViewRestoreScrollY = articleScrollOffset(progress, webViewMaxScrollPx)
            } else {
                val maxValue = snapshotFlow { articleScrollState.maxValue }.first { it > 0 }
                articleScrollState.scrollTo(articleScrollOffset(progress, maxValue))
            }
        },
        onReadingProgressChanged = onReadingProgressChanged,
        onReadingCompleted = onReadingCompleted
    )
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
    val onArticleLinkClick: (String) -> Unit = { url ->
        if (isOnline) {
            onEffect(ArticleRouteEffect.OpenBrowser(url))
        } else {
            onEffect(
                ArticleRouteEffect.CopyText(
                    label = articleLinkLabel,
                    text = url,
                    followUpMessage = offlineLinkCopiedMessage
                )
            )
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
                ArticleContentHeader(
                    entry = entry,
                    feedTitle = feedTitle.uppercase(locale),
                    dateText = dateText,
                    contentProvenance = contentProvenance,
                    contentState = contentState,
                    mainImageUrl = leadImageUrl,
                    onRetryContent = onRetryContent,
                    onZoomImage = { zoomImageUrl = it },
                    bindings = bindings,
                    modifier = Modifier
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
                    ArticleContentHeader(
                        entry = entry,
                        feedTitle = feedTitle.uppercase(locale),
                        dateText = dateText,
                        contentProvenance = contentProvenance,
                        contentState = contentState,
                        mainImageUrl = leadImageUrl,
                        onRetryContent = onRetryContent,
                        onZoomImage = { zoomImageUrl = it },
                        bindings = bindings,
                        modifier = Modifier.onSizeChanged { articleHeaderHeightPx = it.height }
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
                onEffect(ArticleRouteEffect.CopyText(imageUrlLabel, actionsUrl, copiedMessage))
                imageActionsUrl = null
            },
            onDownload = {
                if (isOnline) {
                    dispatchImageDownload(actionsUrl)
                } else {
                    onEffect(ArticleRouteEffect.ShowToast(downloadingRequiresConnectionMessage))
                }
                imageActionsUrl = null
            },
            onShare = {
                if (isOnline) {
                    dispatchImageShare(actionsUrl)
                } else {
                    onEffect(ArticleRouteEffect.ShowToast(sharingRequiresConnectionMessage))
                }
                imageActionsUrl = null
            }
        )
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
