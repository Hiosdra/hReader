package com.hiosdra.hreader.presentation.article

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.FlowPreview
import org.koin.compose.koinInject

@OptIn(FlowPreview::class)
@Composable
internal fun ArticleContent(
    entry: Entry,
    mainImageUrl: String?,
    textScale: Float,
    modifier: Modifier = Modifier,
    articleContent: String,
    contentLoaded: Boolean,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit,
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
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    val imageSharer: ArticleImageSharer = koinInject()
    val locale = LocalLocale.current.platformLocale
    val feedTitle = entry.feed.title.ifBlank { stringResource(R.string.article_unknown_feed) }
    val dateText = remember(entry.publishedAt, locale) { formatArticleDate(entry.publishedAt, locale) }
    val readableArticleContent = articleContent
    val articleScrollState = rememberSaveable(entry.id, saver = ScrollState.Saver) { ScrollState(0) }
    var restoredContentPositionKey by rememberSaveable(entry.id) { mutableStateOf<Int?>(null) }
    var readingCompletionReported by rememberSaveable(entry.id) { mutableStateOf(false) }
    var webContentHeightPx by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var webViewRestoreScrollY by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var webViewScrollProgress by rememberSaveable(entry.id) { mutableFloatStateOf(0f) }
    var articleViewportHeightPx by remember(entry.id) { mutableIntStateOf(0) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    var imageActionsUrl by remember { mutableStateOf<String?>(null) }
    var imageShareUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val articleLinkLabel = stringResource(R.string.article_link)
    val offlineLinkCopiedMessage = stringResource(R.string.article_offline_link_copied)
    val imageUrlLabel = stringResource(R.string.article_image_url)
    val downloadingRequiresConnectionMessage = stringResource(R.string.article_downloading_requires_connection)
    val sharingRequiresConnectionMessage = stringResource(R.string.article_sharing_requires_connection)
    val preparingImageMessage = stringResource(R.string.article_preparing_image)
    val imageSharingFailedMessage = stringResource(R.string.article_image_sharing_failed)
    val safeWebContentHeightPx = safeArticleWebViewHeightPx(webContentHeightPx)
    val webViewHeight = with(LocalDensity.current) { safeWebContentHeightPx.toDp() }
    val webViewNeedsInternalScroll = articleWebViewNeedsInternalScroll(webContentHeightPx)
    val scrollbarMetrics by remember(articleScrollState, webViewNeedsInternalScroll) {
        derivedStateOf {
            if (webViewNeedsInternalScroll) {
                val maxScrollPx = (webContentHeightPx - safeWebContentHeightPx).coerceAtLeast(0)
                verticalScrollbarMetrics(
                    viewportSizePx = safeWebContentHeightPx,
                    contentSizePx = webContentHeightPx,
                    scrollOffsetPx = articleScrollOffset(webViewScrollProgress, maxScrollPx)
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
    val latestOnReadingProgressChanged = rememberUpdatedState(onReadingProgressChanged)
    val latestOnReadingCompleted = rememberUpdatedState(onReadingCompleted)
    val contentFingerprint = readableArticleContent.hashCode()
    val contentPositionKey = contentFingerprint xor if (webViewNeedsInternalScroll) Int.MIN_VALUE else 0

    LaunchedEffect(
        entry.id,
        contentLoaded,
        readingPositionLoaded,
        savedReadingProgress,
        contentPositionKey,
        webContentHeightPx > 0
    ) {
        if (
            restoredContentPositionKey == contentPositionKey ||
            !contentLoaded ||
            !readingPositionLoaded ||
            webContentHeightPx <= 0
        ) {
            return@LaunchedEffect
        }
        val progress = savedReadingProgress
        if (progress == null) {
            restoredContentPositionKey = contentPositionKey
            return@LaunchedEffect
        }

        if (webViewNeedsInternalScroll) {
            webViewRestoreScrollY = articleWebViewRestoreScrollY(
                progress = progress,
                contentHeightPx = webContentHeightPx,
                viewportHeightPx = safeWebContentHeightPx
            )
        } else {
            val maxValue = snapshotFlow { articleScrollState.maxValue }.first { it > 0 }
            articleScrollState.scrollTo(articleScrollOffset(progress, maxValue))
        }
        restoredContentPositionKey = contentPositionKey
    }

    LaunchedEffect(entry.id, readingPositionLoaded, webViewNeedsInternalScroll) {
        if (!readingPositionLoaded) return@LaunchedEffect
        readingCompletionReported = false
        snapshotFlow {
            if (webViewNeedsInternalScroll) {
                webViewScrollProgress to true
            } else {
                val maxValue = articleScrollState.maxValue
                articleScrollProgress(articleScrollState.value, maxValue) to (maxValue > 0)
            }
        }
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

    DisposableEffect(entry.id, webViewNeedsInternalScroll) {
        onDispose {
            if (!latestReadingPositionLoaded.value) return@onDispose
            val maxValue = articleScrollState.maxValue
            if (!webViewNeedsInternalScroll && maxValue <= 0) return@onDispose
            val progress = if (webViewNeedsInternalScroll) {
                webViewScrollProgress
            } else {
                articleScrollProgress(articleScrollState.value, maxValue)
            }
            if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                latestOnReadingCompleted.value(entry.id)
            } else {
                latestOnReadingProgressChanged.value(entry.id, progress)
            }
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
                        scrollEnabled = webViewNeedsInternalScroll,
                        restoreScrollY = webViewRestoreScrollY,
                        onScrollProgress = { progress -> webViewScrollProgress = progress },
                        onContentHeightChanged = { height -> webContentHeightPx = height },
                        onLinkClick = { url ->
                            if (isOnline) {
                                openChromeCustomTab(context, url)
                            } else {
                                copyTextToClipboard(context, articleLinkLabel, url)
                                Toast.makeText(context, offlineLinkCopiedMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImageLongClick = { url -> imageActionsUrl = url }
                    )
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
                    enqueueImageDownload(context, actionsUrl)
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
            ZoomableImage(entryId = entry.id, url = zoomUrl) { zoomImageUrl = null }
        }
    }
}

private fun formatArticleDate(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
