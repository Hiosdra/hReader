package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.presentation.components.OfflineAwareImage
import com.hiosdra.hreader.presentation.theme.HReaderSpacing

@Composable
internal fun ArticleContentHeader(
    entry: Entry,
    feedTitle: String,
    dateText: String,
    contentProvenance: ArticleContentProvenance,
    contentState: ArticleContentLoadState,
    mainImageUrl: String?,
    onRetryContent: () -> Unit,
    onZoomImage: (String) -> Unit,
    bindings: ArticlePagerBindings,
    modifier: Modifier = Modifier
) {
    val state = bindings.state
    val content = state.content
    val ai = state.ai
    val isOnline = content.isOnline
    val onAiOverview = bindings.onAiOverview?.let { callback -> { callback(entry.id) } }
    val onAnalyzeCredibility: ((Boolean) -> Unit)? = bindings.onAnalyzeCredibility?.let {
        callback -> { force: Boolean -> callback(entry.id, force) }
    }
    val onOpenOriginal = entry.url.takeIf { isOnline && it.isNotBlank() }?.let {
        { bindings.onOpenInChrome(it) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(top = HReaderSpacing.space3)
    ) {
        Text(
            text = feedTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = HReaderSpacing.space2)
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(HReaderSpacing.space3))
        ArticleMetadata(
            author = entry.author,
            dateText = dateText,
            readingTimeMinutes = entry.readingTime,
            isOnline = isOnline,
            aiProvider = ai.aiProvider,
            aiOverview = ai.aiOverviews[entry.id],
            isGeneratingOverview = entry.id in ai.generatingOverviewIds,
            aiOverviewProgress = ai.aiOverviewProgress[entry.id],
            onAiOverviewClick = onAiOverview,
            credibilityEnabled = ai.credibilityEnabled,
            credibilityReport = ai.credibilityReports[entry.id],
            isAnalyzingCredibility = entry.id in ai.analyzingCredibilityIds,
            onAnalyzeCredibility = onAnalyzeCredibility
        )
        Spacer(modifier = Modifier.height(HReaderSpacing.space2))
        ArticleContentProvenanceStatus(
            provenance = contentProvenance,
            contentState = contentState,
            onRetry = onRetryContent,
            onOpenOriginal = onOpenOriginal
        )
        Spacer(modifier = Modifier.height(HReaderSpacing.space3))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        mainImageUrl?.let { imageUrl ->
            Spacer(modifier = Modifier.height(HReaderSpacing.space5))
            OfflineAwareImage(
                entryId = entry.id,
                imageUrl = imageUrl,
                contentDescription = null,
                isOnline = isOnline,
                articleImageLoader = bindings.articleImageLoader,
                coilImageLoader = bindings.coilImageLoader,
                remoteResourcePolicy = bindings.remoteResourcePolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onZoomImage(imageUrl) },
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(HReaderSpacing.space6))
    }
}
