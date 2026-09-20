package com.hiosdra.hreader.presentation.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.theme.MotionDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
internal data class MainTopBarBindings(
    val uiState: MainUiState,
    val unreadCount: Int,
    val feedId: Long?,
    val scrollBehavior: TopAppBarScrollBehavior,
    val searchActive: MutableState<Boolean>,
    val searchFocusRequester: FocusRequester,
    val keyboardController: SoftwareKeyboardController?,
    val offlineBannerDismissed: Boolean,
    val onDismissOfflineBanner: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissAiModelWarning: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onShowReadArticles: (Boolean) -> Unit,
    val onPrepareOffline: () -> Boolean,
    val onLeaveFeed: () -> Unit,
    val snackbarHostState: SnackbarHostState,
    val snackbarScope: CoroutineScope,
    val offlineStartedMessage: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(bindings: MainTopBarBindings) {
    val uiState = bindings.uiState
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!uiState.isOnline && !bindings.offlineBannerDismissed) {
            OfflineBanner(onDismiss = bindings.onDismissOfflineBanner)
        }
        uiState.unavailableAiModelId?.let { modelId ->
            AiModelUnavailableBanner(
                modelId = modelId,
                onOpenSettings = bindings.onOpenSettings,
                onDismiss = bindings.onDismissAiModelWarning
            )
        }
        if (uiState.offlinePreparation.isRunning) {
            OfflinePreparationProgressBanner(uiState.offlinePreparation)
        }
        TopAppBar(
            title = {
                val listTitle = uiState.feedTitle ?: stringResource(R.string.main_all_articles)
                Text(
                    if (bindings.unreadCount > 0) {
                        stringResource(
                            R.string.main_title_with_unread_count,
                            listTitle,
                            bindings.unreadCount
                        )
                    } else {
                        listTitle
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = bindings.onOpenSubscriptions,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.main_feeds),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val closing = bindings.searchActive.value
                        bindings.searchActive.value = !closing
                        if (closing) {
                            bindings.onSearchQueryChange("")
                            bindings.keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    AnimatedContent(
                        targetState = bindings.searchActive.value,
                        transitionSpec = {
                            (fadeIn(
                                animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                            ) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                            )) togetherWith (fadeOut(
                                animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                            ) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                            ))
                        },
                        label = "search action icon"
                    ) { active ->
                        Icon(
                            if (active) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (active) R.string.main_close_search else R.string.action_search
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (uiState.isOnline && !uiState.isRefreshing) {
                            bindings.onRefresh()
                        }
                    },
                    enabled = !uiState.isRefreshing,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                val expanded = remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { expanded.value = true },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        if (uiState.readCount > 0 || uiState.showReadArticles) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (uiState.showReadArticles) {
                                            stringResource(R.string.main_unread_only)
                                        } else {
                                            stringResource(R.string.main_show_read_articles, uiState.readCount)
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                onClick = {
                                    expanded.value = false
                                    bindings.onShowReadArticles(!uiState.showReadArticles)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Done,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.offline_download_reading),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            onClick = {
                                expanded.value = false
                                if (bindings.onPrepareOffline()) {
                                    bindings.snackbarScope.launch {
                                        bindings.snackbarHostState.showFeedback(
                                            FeedbackRequest(message = bindings.offlineStartedMessage)
                                        )
                                    }
                                }
                            },
                            enabled = uiState.isOnline && !uiState.offlinePreparation.isRunning
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.main_settings),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            onClick = {
                                expanded.value = false
                                bindings.onOpenSettings()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            scrollBehavior = bindings.scrollBehavior
        )
        AnimatedVisibility(
            visible = bindings.searchActive.value,
            enter = expandVertically(
                animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
            ) + fadeIn(
                animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
            ),
            exit = shrinkVertically(
                animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
            ) + fadeOut(
                animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = bindings.onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(bindings.searchFocusRequester),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.main_search_articles)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { bindings.keyboardController?.hide() }
                    ),
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { bindings.onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
        ArticleScopeBar(
            feedId = bindings.feedId,
            feedTitle = uiState.feedTitle,
            searchQuery = uiState.searchQuery,
            showReadArticles = uiState.showReadArticles,
            onShowReadArticles = bindings.onShowReadArticles,
            onClearSearch = { bindings.onSearchQueryChange("") },
            onLeaveFeed = bindings.onLeaveFeed
        )
    }
}
