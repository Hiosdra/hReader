package com.hiosdra.hreader.ui.article

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.ArticleListEntry
import com.hiosdra.hreader.data.model.isRead
import com.hiosdra.hreader.ui.components.OfflineAwareImage
import com.hiosdra.hreader.ui.theme.sectionCardColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ArticleRow(
    entry: ArticleListEntry,
    onOpen: (Long) -> Unit,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val checked = entry.isRead
    val locale = LocalLocale.current.platformLocale
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    val feedTitle = entry.feed.title.ifBlank { stringResource(R.string.article_unknown_feed) }
    val readStateDescription = stringResource(
        if (checked) R.string.article_read else R.string.article_unread
    )
    val readStatusActionDescription = stringResource(readStatusActionLabel(checked))

    // Read rows are dimmed, not hidden. Below this the summary drops under the 4.5:1 needed to
    // stay readable, and a read article still has to be re-findable by eye.
    val contentAlpha by animateFloatAsState(targetValue = if (checked) 0.70f else 1f, label = "alpha")
    val titleWeight = if (checked) FontWeight.Normal else FontWeight.SemiBold
    val indicatorColor by animateColorAsState(
        targetValue = if (checked) {
            // Solid, because the row already dims it. Fading it as well left it invisible, and
            // outline against the accent is contrast enough to tell the two states apart.
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "indicator"
    )

    Card(
        onClick = { onOpen(entry.id) },
        modifier = Modifier
            .fillMaxWidth()
            // Two adjacent cards each contribute their vertical margin, so the gap between
            // them is twice this. The list draws no divider between rows, only the spacing.
            .padding(horizontal = 12.dp, vertical = 3.dp)
            // Read state reaches a screen reader as state rather than as a colour and an opacity,
            // which is all a sighted reader was ever given.
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
                                text = feedTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeFormatter.format(entry.publishedAt),
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
                // Read state is the only thing a row acts on. Starring belongs to the article the
                // reader has actually opened, where there is room to say what it means.
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
