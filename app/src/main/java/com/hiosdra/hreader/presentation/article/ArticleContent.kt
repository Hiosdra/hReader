package com.hiosdra.hreader.presentation.article

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.hiosdra.hreader.core.application.content.removeDuplicateArticleTitle
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.domain.model.CredibilityConfidence
import com.hiosdra.hreader.core.domain.model.CredibilityLevel
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.presentation.components.OfflineAwareImage
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import com.hiosdra.hreader.presentation.theme.LocalCredibilityColors
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
    isGeneratingOverview: Boolean = false,
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
    val readableArticleContent = remember(articleContent, entry.title) {
        removeDuplicateArticleTitle(articleContent, entry.title)
    }
    val articleScrollState = rememberSaveable(entry.id, saver = ScrollState.Saver) { ScrollState(0) }
    var restoredContentPositionKey by rememberSaveable(entry.id) { mutableStateOf<Int?>(null) }
    var readingCompletionReported by rememberSaveable(entry.id) { mutableStateOf(false) }
    var webContentHeightPx by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var webViewRestoreScrollY by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var webViewScrollProgress by rememberSaveable(entry.id) { mutableFloatStateOf(0f) }
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
    val scrollProgress by remember(articleScrollState, webViewNeedsInternalScroll) {
        derivedStateOf {
            if (webViewNeedsInternalScroll) {
                webViewScrollProgress
            } else {
                articleScrollProgress(articleScrollState.value, articleScrollState.maxValue)
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
                        scrollEnabled = webViewNeedsInternalScroll,
                        restoreScrollY = webViewRestoreScrollY,
                        onScrollProgress = { progress -> webViewScrollProgress = progress },
                        onContentHeightChanged = { height -> webContentHeightPx = height },
                        onLinkClick = { url ->
                            // A custom tab offline is a browser error page, and the link is gone
                            // by the time the reader is back in range.
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
        if (readingTimeMinutes != null && readingTimeMinutes > 0) {
            add(pluralStringResource(R.plurals.article_reading_time_minutes, readingTimeMinutes, readingTimeMinutes))
        }
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
                                contentDescription = stringResource(R.string.article_ai_summary),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val chipText = when {
                                aiOverview == null && isGeneratingOverview ->
                                    stringResource(R.string.article_generating_summary)
                                aiOverview == null -> stringResource(R.string.article_ai_summary)
                                isAiExpanded.value -> stringResource(R.string.article_hide_summary)
                                else -> stringResource(R.string.article_show_summary)
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

        // AI summary Content with Animation
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
                                contentDescription = stringResource(R.string.article_ai_summary),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.article_ai_summary),
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
                                    text = stringResource(R.string.article_generating_summary),
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
                    isAnalyzing -> stringResource(R.string.article_analyzing)
                    report == null -> stringResource(R.string.article_check_credibility)
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
                    text = if (report == null) {
                        stringResource(R.string.article_credibility)
                    } else {
                        credibilityLevelLabel(report.level)
                    },
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
                        text = stringResource(R.string.article_analyzing_article),
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
                    title = stringResource(R.string.article_model_saw),
                    items = report.reasons,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (report.redFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CredibilityBulletList(
                    title = stringResource(R.string.article_red_flags),
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
                Text(stringResource(R.string.article_reanalyze))
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

@Composable
private fun credibilityLevelLabel(level: CredibilityLevel): String = when (level) {
    CredibilityLevel.HIGH -> stringResource(R.string.credibility_high)
    CredibilityLevel.MIXED -> stringResource(R.string.credibility_mixed)
    CredibilityLevel.LOW -> stringResource(R.string.credibility_low)
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

@Composable
private fun credibilityDisclaimer(report: CredibilityReport): String {
    val locale = LocalLocale.current.platformLocale
    return buildString {
        append(stringResource(R.string.credibility_ai_estimate))
        append(
            stringResource(
                when (report.confidence) {
                    CredibilityConfidence.HIGH -> R.string.credibility_confidence_high
                    CredibilityConfidence.MEDIUM -> R.string.credibility_confidence_medium
                    CredibilityConfidence.LOW -> R.string.credibility_confidence_low
                }
            )
        )
        if (report.contentTruncated) append(stringResource(R.string.credibility_long_article))
        append(
            stringResource(
                R.string.credibility_analyzed,
                formatTimestamp(report.analyzedAt, locale),
                report.modelId
            )
        )
    }
}

private fun formatTimestamp(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)

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
                headlineContent = { Text(stringResource(R.string.article_view_image)) },
                modifier = Modifier.clickable(onClick = onView)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_copy_image_url)) },
                modifier = Modifier.clickable(onClick = onCopy)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_download_image)) },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onDownload)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_share_image)) },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onShare)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.article_copied), Toast.LENGTH_SHORT).show()
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
    val message = context.getString(if (started) R.string.article_downloading else R.string.article_download_failed)
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
