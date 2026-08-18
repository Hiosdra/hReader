package com.hiosdra.hreader.presentation.article

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.CredibilityReport

@Composable
internal fun ArticleMetadata(
    author: String?,
    dateText: String,
    readingTimeMinutes: Int?,
    isOnline: Boolean = true,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    onAiOverviewClick: (() -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Boolean) -> Unit)? = null
) {
    var isAiExpanded by rememberSaveable { mutableStateOf(false) }
    var isCredibilityExpanded by rememberSaveable { mutableStateOf(false) }

    val aiOverviewClick = onAiOverviewClick
    val analyzeCredibility = if (credibilityEnabled) onAnalyzeCredibility else null

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
                            isAiExpanded = true
                            aiOverviewClick()
                        } else {
                            isAiExpanded = !isAiExpanded
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
                                isAiExpanded -> stringResource(R.string.article_hide_summary)
                                else -> stringResource(R.string.article_show_summary)
                            }
                            Text(chipText)
                        }
                    },
                    enabled = !(aiOverview == null && isGeneratingOverview) && (aiOverview != null || isOnline),
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = if (aiOverview != null || isGeneratingOverview) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
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
                            isCredibilityExpanded = true
                            analyzeCredibility(false)
                        } else {
                            isCredibilityExpanded = !isCredibilityExpanded
                        }
                    }
                )
            }
        }

        if ((aiOverview != null || isGeneratingOverview) && isAiExpanded) {
            AnimatedVisibility(
                visible = isAiExpanded,
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

        if (credibilityEnabled && isCredibilityExpanded &&
            (credibilityReport != null || isAnalyzingCredibility)
        ) {
            AnimatedVisibility(
                visible = isCredibilityExpanded,
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
