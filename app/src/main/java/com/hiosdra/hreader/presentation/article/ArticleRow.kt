package com.hiosdra.hreader.presentation.article

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.components.OfflineAwareImage
import com.hiosdra.hreader.presentation.theme.sectionCardColors
import com.hiosdra.hreader.presentation.theme.MotionDuration

@Composable
internal fun ArticleRow(
    entry: ArticleRowModel,
    onOpen: (Long) -> Unit,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit,
    onMarkReadThrough: (Long) -> Unit,
    onSwipeReadStatus: (entryId: Long, checked: Boolean) -> Unit,
    imageDependencies: ArticleImageDependencies,
    readStateAnimationEnabled: Boolean,
    isOnline: Boolean = true,
    localImagePath: String? = null
) {
    val checked = entry.isRead
    val readStateDescription = stringResource(
        if (checked) R.string.article_read else R.string.article_unread
    )
    val readStatusActionDescription = stringResource(readStatusActionLabel(checked))
    val articleActionsDescription = stringResource(R.string.main_article_actions)
    var actionsExpanded by remember { mutableStateOf(false) }
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeReadStatus(entry.id, true)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeReadStatus(entry.id, false)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    // Read rows are dimmed, not hidden. Below this the summary drops under the 4.5:1 needed to
    // stay readable, and a read article still has to be re-findable by eye.
    val targetAlpha = if (checked) 0.70f else 1f
    val contentAlpha = if (readStateAnimationEnabled) {
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK)),
            label = "alpha"
        ).value
    } else {
        targetAlpha
    }
    val titleWeight = if (checked) FontWeight.Normal else FontWeight.SemiBold
    val targetIndicatorColor = if (checked) {
        // Solid, because the row already dims it. Fading it as well left it invisible, and
        // outline against the accent is contrast enough to tell the two states apart.
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.primary
    }
    val indicatorColor = if (readStateAnimationEnabled) {
        animateColorAsState(
            targetValue = targetIndicatorColor,
            animationSpec = tween(MotionDuration.scaled(MotionDuration.QUICK)),
            label = "indicator"
        ).value
    } else {
        targetIndicatorColor
    }

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !checked,
        enableDismissFromEndToStart = checked,
        backgroundContent = { TriageSwipeBackground(isRead = checked) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Card(
            onClick = { onOpen(entry.id) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = readStateDescription
                },
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = sectionCardColors()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            // Decoration: the same fact is already announced as state on the card.
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                                    .clearAndSetSemantics { }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // The feed name yields space instead of taking all of it. Unweighted it
                            // was measured first, and against a long name in a row narrowed by a
                            // thumbnail the time was left a single character wide, one digit per line.
                            Text(
                                text = entry.feedTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.publishedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = titleWeight),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Plain text by the time it is stored. Deriving it here ran an HTML parse
                        // and four regexes per row, on the frame that scrolls the list.
                        entry.preview?.takeIf { it.isNotBlank() }?.let { preview ->
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (entry.imageUrl != null) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center
                        ) {
                            // Fills the square it was given: at fillMaxWidth the height followed the
                            // source aspect ratio, so a portrait photo stood taller than its slot.
                            // No description — it illustrates the headline that is read out anyway.
                            OfflineAwareImage(
                                entryId = entry.id,
                                imageUrl = entry.imageUrl,
                                contentDescription = null,
                                isOnline = isOnline,
                                articleImageLoader = imageDependencies.articleImageLoader,
                                coilImageLoader = imageDependencies.coilImageLoader,
                                remoteResourcePolicy = imageDependencies.remoteResourcePolicy,
                                localImagePath = localImagePath,
                                lookupLocalPath = false,
                                checkRemotePolicy = false,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0f),
                                                Color.Black.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Box {
                            IconButton(
                                onClick = { actionsExpanded = true },
                                modifier = Modifier.semantics {
                                    contentDescription = articleActionsDescription
                                }
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(readStatusActionLabel(checked)))
                                    },
                                    onClick = {
                                        actionsExpanded = false
                                        onCheckedChange(entry.id, !checked)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.main_mark_through_here)) },
                                    onClick = {
                                        actionsExpanded = false
                                        onMarkReadThrough(entry.id)
                                    }
                                )
                            }
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onCheckedChange(entry.id, it) },
                            modifier = Modifier.semantics {
                                contentDescription = readStatusActionDescription
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TriageSwipeBackground(isRead: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = if (isRead) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = stringResource(
                if (isRead) R.string.main_swipe_mark_unread else R.string.main_swipe_mark_read
            ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
