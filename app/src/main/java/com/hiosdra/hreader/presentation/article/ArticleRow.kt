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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Card(
        onClick = { onOpen(entry.id) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                                    .clearAndSetSemantics { }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
