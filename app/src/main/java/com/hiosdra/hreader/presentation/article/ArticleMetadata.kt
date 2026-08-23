package com.hiosdra.hreader.presentation.article

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.presentation.theme.MotionDuration

private enum class CloudAiAction {
    SUMMARY,
    CREDIBILITY
}

@Composable
internal fun ArticleMetadata(
    author: String?,
    dateText: String,
    readingTimeMinutes: Int?,
    isOnline: Boolean = true,
    aiProvider: AiProvider = AiProvider.OPENROUTER,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    aiOverviewProgress: ArticleAiProgress? = null,
    onAiOverviewClick: (() -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Boolean) -> Unit)? = null
) {
    var isAiExpanded by rememberSaveable { mutableStateOf(false) }
    var isCredibilityExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingCloudAction by rememberSaveable { mutableStateOf<CloudAiAction?>(null) }

    val aiOverviewClick = onAiOverviewClick
    val analyzeCredibility = if (credibilityEnabled) onAnalyzeCredibility else null
    val isLocalAi = aiProvider == AiProvider.GEMMA_LOCAL

    fun startAiAction(action: CloudAiAction, start: () -> Unit) {
        if (isLocalAi) {
            start()
        } else {
            pendingCloudAction = action
        }
    }

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
                            if (isGeneratingOverview) {
                                isAiExpanded = !isAiExpanded
                            } else {
                                isAiExpanded = true
                                startAiAction(CloudAiAction.SUMMARY, aiOverviewClick)
                            }
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
                                aiOverview == null && isGeneratingOverview -> articleAiProgressLabel(aiOverviewProgress)
                                aiOverview == null -> stringResource(R.string.article_ai_summary)
                                isAiExpanded -> stringResource(R.string.article_hide_summary)
                                else -> stringResource(R.string.article_show_summary)
                            }
                            Text(chipText)
                        }
                    },
                    enabled = isGeneratingOverview || aiOverview != null || isOnline || isLocalAi,
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
                    enabled = credibilityReport != null || isOnline || isLocalAi,
                    onClick = {
                        if (credibilityReport == null) {
                            isCredibilityExpanded = true
                            startAiAction(CloudAiAction.CREDIBILITY) { analyzeCredibility(false) }
                        } else {
                            isCredibilityExpanded = !isCredibilityExpanded
                        }
                    }
                )
            }
        }

        if (aiOverview != null || isGeneratingOverview) {
            AnimatedVisibility(
                visible = isAiExpanded,
                enter = slideInVertically(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                ) + fadeIn(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                ),
                exit = slideOutVertically(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                ) + fadeOut(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                )
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
                        AnimatedContent(
                            targetState = isGeneratingOverview,
                            transitionSpec = {
                                (fadeIn(
                                    animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                                ) + scaleIn(
                                    initialScale = 0.98f,
                                    animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                                )) togetherWith (fadeOut(
                                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                                ) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                                ))
                            },
                            label = "AI summary content"
                        ) { generating ->
                            if (generating) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = articleAiProgressLabel(aiOverviewProgress),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    aiOverviewProgress?.draft
                                        ?.takeIf(String::isNotBlank)
                                        ?.let { draft ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.article_ai_working_summary),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = draft,
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
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
        }

        if (aiOverviewClick != null || analyzeCredibility != null) {
            Text(
                text = stringResource(
                    if (isLocalAi) {
                        R.string.article_ai_on_device_processing
                    } else {
                        R.string.article_ai_cloud_processing
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (credibilityEnabled && (credibilityReport != null || isAnalyzingCredibility)) {
            AnimatedVisibility(
                visible = isCredibilityExpanded,
                enter = slideInVertically(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                ) + fadeIn(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                ),
                exit = slideOutVertically(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                ) + fadeOut(
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                )
            ) {
                AnimatedContent(
                    targetState = isAnalyzingCredibility,
                    transitionSpec = {
                        (fadeIn(
                            animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                        ) + scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                        )) togetherWith (fadeOut(
                            animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                        ) + scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                        ))
                    },
                    label = "credibility content"
                ) { analyzing ->
                    CredibilityCard(
                        report = credibilityReport,
                        isAnalyzing = analyzing,
                        onReanalyze = {
                            analyzeCredibility?.let { callback ->
                                startAiAction(CloudAiAction.CREDIBILITY) { callback(true) }
                            }
                        }
                    )
                }
            }
        }

        pendingCloudAction?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingCloudAction = null },
                title = { Text(stringResource(R.string.article_ai_cloud_confirmation_title)) },
                text = {
                    Text(
                        stringResource(
                            when (action) {
                                CloudAiAction.SUMMARY -> R.string.article_ai_cloud_summary_confirmation
                                CloudAiAction.CREDIBILITY -> R.string.article_ai_cloud_credibility_confirmation
                            }
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingCloudAction = null
                            when (action) {
                                CloudAiAction.SUMMARY -> aiOverviewClick?.invoke()
                                CloudAiAction.CREDIBILITY -> analyzeCredibility?.invoke(false)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.article_ai_cloud_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloudAction = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun articleAiProgressLabel(progress: ArticleAiProgress?): String = when (progress?.phase) {
    ArticleAiPhase.PREPARING -> stringResource(R.string.article_ai_preparing)
    ArticleAiPhase.LOADING_MODEL -> stringResource(R.string.article_ai_loading_model)
    ArticleAiPhase.COMPACTING -> progressPartLabel(
        progress,
        R.string.article_ai_compacting
    )
    ArticleAiPhase.THINKING -> progressPartLabel(
        progress,
        R.string.article_ai_thinking
    )
    ArticleAiPhase.STREAMING -> progressPartLabel(
        progress,
        R.string.article_ai_streaming
    )
    ArticleAiPhase.FINALIZING -> stringResource(R.string.article_ai_finalizing)
    null -> stringResource(R.string.article_generating_summary)
}

@Composable
private fun progressPartLabel(progress: ArticleAiProgress, resourceId: Int): String =
    if (progress.part > 0 && progress.totalParts > 0) {
        stringResource(resourceId, progress.part, progress.totalParts)
    } else {
        stringResource(R.string.article_generating_summary)
    }
