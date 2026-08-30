package com.hiosdra.hreader.presentation.article

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.domain.model.isRead
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.theme.MotionDuration
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleTopBar(
    entryUrl: String?,
    feedTitle: String?,
    listPosition: Int,
    listSize: Int,
    isWebViewMode: Boolean,
    canUseWebView: Boolean,
    isRead: Boolean,
    textScale: Float,
    onDecreaseTextScale: () -> Unit,
    onResetTextScale: () -> Unit,
    onIncreaseTextScale: () -> Unit,
    onToggleRead: () -> Unit,
    onBack: () -> Unit,
    onToggleWebView: () -> Unit,
    onShare: () -> Unit,
    ttsContentState: ArticleTtsContentState? = null,
    isTtsActive: Boolean = false,
    onInvokeTts: () -> Unit = {}
) {
    TopAppBar(
        title = {
            // One line, cut short if it has to be. A feed named "Subiektywnie o finansach — Maciej
            // Samcik" wrapped to four of them, which grew the bar over the status bar above it and
            // the article below.
            Column {
                Text(
                    text = feedTitle ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (listPosition in 1..listSize) {
                    Text(
                        text = stringResource(R.string.article_position, listPosition, listSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
            Box {
                IconButton(onClick = { overflowExpanded.value = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
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
                    ttsContentState?.let { contentState ->
                        ArticleTtsMenuItem(
                            isTtsActive = isTtsActive,
                            contentState = contentState,
                            onClick = {
                                overflowExpanded.value = false
                                onInvokeTts()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (entryUrl != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
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
                            text = { Text(stringResource(R.string.article_decrease_text_size)) },
                            onClick = {
                                overflowExpanded.value = false
                                onDecreaseTextScale()
                            },
                            enabled = textScale > MIN_ARTICLE_TEXT_SCALE,
                            leadingIcon = { Text("A−") }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.article_reset_text_size, (textScale * 100).roundToInt()))
                            },
                            onClick = {
                                overflowExpanded.value = false
                                onResetTextScale()
                            },
                            leadingIcon = { Text("A") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.article_increase_text_size)) },
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
private fun ArticleTtsMenuItem(
    isTtsActive: Boolean,
    contentState: ArticleTtsContentState,
    onClick: () -> Unit
) {
    val (label, enabled) = when {
        isTtsActive -> R.string.article_open_tts_player to true
        contentState == ArticleTtsContentState.LOADING -> R.string.article_missing_content to false
        contentState == ArticleTtsContentState.UNAVAILABLE -> R.string.article_read_aloud_unavailable to false
        else -> R.string.article_read_aloud to true
    }
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
    )
}

@Composable
private fun ReadStatusButton(
    isRead: Boolean,
    onToggleRead: () -> Unit
) {
    val actionDescription = stringResource(readStatusActionLabel(isRead))
    val stateDescription = stringResource(if (isRead) R.string.article_read else R.string.article_unread)
    val iconTint by animateColorAsState(
        targetValue = if (isRead) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK)),
        label = "read status color"
    )

    IconButton(
        onClick = onToggleRead,
        modifier = Modifier.semantics {
            contentDescription = actionDescription
            this.stateDescription = stateDescription
        }
    ) {
        AnimatedContent(
            targetState = isRead,
            transitionSpec = {
                (fadeIn(animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK))
                    )) togetherWith
                    (fadeOut(animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))) +
                        scaleOut(
                            targetScale = 0.8f,
                            animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                        ))
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
                label = stringResource(R.string.article_feed_tab),
                selected = !isWebViewMode,
                enabled = true,
                contentDescription = stringResource(R.string.article_show_feed_content),
                onClick = { if (isWebViewMode) onToggleWebView() }
            )
            ReaderModeOption(
                label = stringResource(R.string.article_web_tab),
                selected = isWebViewMode,
                enabled = canUseWebView,
                contentDescription = stringResource(
                    if (canUseWebView) R.string.article_show_original_web_page
                    else R.string.article_web_unavailable_offline
                ),
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
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK)),
        label = "reader mode container"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK)),
        label = "reader mode text"
    )

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun ArticleBottomLinkBar(
    modifier: Modifier = Modifier,
    entryUrl: String?,
    isOnline: Boolean,
    canUsePaywallBypass: Boolean,
    onOpenInChrome: () -> Unit,
    onBypassPaywall: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = ARTICLE_BOTTOM_BAR_ALPHA),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (entryUrl != null) {
                IconButton(onClick = onOpenInChrome, enabled = isOnline) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chrome_logo),
                        contentDescription = stringResource(R.string.article_open_original_in_chrome),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (canUsePaywallBypass) {
                    IconButton(onClick = onBypassPaywall, enabled = isOnline) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.article_open_through_paywall_bypass),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
