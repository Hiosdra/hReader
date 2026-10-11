package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.text.resolve

internal data class ArticleScreenPresentationState(
    val navigation: ArticleNavigationState,
    val content: ArticleContentState,
    val ai: ArticleAiState,
    val currentEntry: Entry?,
    val currentWebViewActive: Boolean,
    val textScale: Float,
    val topBar: ArticleScreenTopBarState,
    val topBarActions: ArticleScreenTopBarActions,
    val pagerState: PagerState,
    val pagerBindings: ArticlePagerBindings,
    val bottomContentPadding: Dp,
    val snackbarHostState: SnackbarHostState,
    val ttsState: ArticleTtsState,
    val ttsPlayerContentState: ArticleTtsContentState?,
    val ttsPlayerSheetVisible: Boolean,
    val temporaryTtsModel: TtsModel?,
    val configuredTtsModel: TtsModel,
    val ttsModelStatuses: Map<TtsModel, TtsModelStatus>,
    val paywallMethodPickerVisible: Boolean,
    val defaultPaywallBypassMethod: PaywallBypassMethod
)

internal data class ArticleScreenPresentationActions(
    val onRetryNavigation: () -> Unit,
    val onGenerateAiOverview: (Long) -> Unit,
    val onClearOverviewError: () -> Unit,
    val onGenerateAiArticleSummary: (Long) -> Unit,
    val onClearArticleSummaryError: () -> Unit,
    val onRetryContent: (Long) -> Unit,
    val onClearContentError: () -> Unit,
    val onAnalyzeCredibility: (Long) -> Unit,
    val onClearScoreError: () -> Unit,
    val onOpenTtsPlayer: () -> Unit,
    val onPauseTts: () -> Unit,
    val onResumeTts: () -> Unit,
    val onStopTts: () -> Unit,
    val onTtsMiniPlayerSizeChanged: (Int) -> Unit,
    val onTemporaryTtsModelChanged: (TtsModel?) -> Unit,
    val onRetryTts: () -> Unit,
    val onDismissTtsPlayer: () -> Unit,
    val onSelectPaywallMethod: (PaywallBypassMethod) -> Unit,
    val onDismissPaywallPicker: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleScreenPresentation(
    state: ArticleScreenPresentationState,
    actions: ArticleScreenPresentationActions
) {
    val navigation = state.navigation
    val content = state.content
    val ai = state.ai
    val currentEntryId = state.currentEntry?.id
    val bottomContentPadding = state.bottomContentPadding
    val ttsState = state.ttsState

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = state.snackbarHostState,
                modifier = Modifier.padding(bottom = bottomContentPadding)
            )
        },
        topBar = {
            ArticleScreenTopBar(state = state.topBar, actions = state.topBarActions)
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                navigation.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = bottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                navigation.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = bottomContentPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = navigation.error.resolve(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = actions.onRetryNavigation,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                navigation.entries.isNotEmpty() -> {
                    ArticlePager(
                        entries = navigation.entries,
                        pagerState = state.pagerState,
                        isWebViewMode = state.currentWebViewActive,
                        textScale = state.textScale,
                        paddingValues = paddingValues,
                        bottomContentPadding = bottomContentPadding,
                        bindings = state.pagerBindings
                    )
                }
            }

            ai.overviewError?.let { error ->
                RetryableSnackbar(
                    hostState = state.snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null
                    },
                    onAction = { currentEntryId?.let(actions.onGenerateAiOverview) },
                    onDismissed = actions.onClearOverviewError
                )
            }

            ai.articleSummaryError?.let { error ->
                RetryableSnackbar(
                    hostState = state.snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null
                    },
                    onAction = { currentEntryId?.let(actions.onGenerateAiArticleSummary) },
                    onDismissed = actions.onClearArticleSummaryError
                )
            }

            content.contentError?.let { message ->
                if (currentEntryId != null) {
                    ArticleContentErrorBanner(
                        message = message.resolve(),
                        onRetry = { actions.onRetryContent(currentEntryId) },
                        onDismiss = actions.onClearContentError,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomContentPadding + 8.dp)
                            .navigationBarsPadding()
                    )
                }
            }

            ai.scoreError?.let { error ->
                RetryableSnackbar(
                    hostState = state.snackbarHostState,
                    message = error.resolve(),
                    actionLabel = stringResource(R.string.action_retry).takeIf {
                        currentEntryId != null
                    },
                    onAction = {
                        currentEntryId?.let { actions.onAnalyzeCredibility(it) }
                    },
                    onDismissed = actions.onClearScoreError
                )
            }

            if (ttsState.articleId != null) {
                ArticleTtsMiniPlayer(
                    state = ttsState,
                    onOpen = actions.onOpenTtsPlayer,
                    onPause = actions.onPauseTts,
                    onResume = actions.onResumeTts,
                    onStop = actions.onStopTts,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .navigationBarsPadding(),
                    onSizeChanged = actions.onTtsMiniPlayerSizeChanged
                )
            }

            if (state.ttsPlayerSheetVisible && ttsState.articleId != null) {
                ArticleTtsPlayerSheet(
                    state = ttsState,
                    temporaryModel = state.temporaryTtsModel,
                    configuredModel = state.configuredTtsModel,
                    modelStatuses = state.ttsModelStatuses,
                    contentState = state.ttsPlayerContentState,
                    onTemporaryModelChange = actions.onTemporaryTtsModelChanged,
                    onPause = actions.onPauseTts,
                    onResume = actions.onResumeTts,
                    onStop = actions.onStopTts,
                    onRetry = actions.onRetryTts,
                    onDismiss = actions.onDismissTtsPlayer
                )
            }

            if (state.paywallMethodPickerVisible && state.currentEntry != null) {
                PaywallBypassMethodPicker(
                    defaultPaywallBypassMethod = state.defaultPaywallBypassMethod,
                    onSelect = actions.onSelectPaywallMethod,
                    onDismiss = actions.onDismissPaywallPicker
                )
            }
        }
    }
}

@Composable
private fun ArticleContentErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
        androidx.compose.foundation.layout.Row(
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
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun RetryableSnackbar(
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismissed: () -> Unit
) {
    LaunchedEffect(message) {
        hostState.showFeedback(
            FeedbackRequest(
                message = message,
                kind = FeedbackKind.RECOVERABLE_ERROR,
                actionLabel = actionLabel,
                onAction = onAction
            )
        )
        onDismissed()
    }
}
