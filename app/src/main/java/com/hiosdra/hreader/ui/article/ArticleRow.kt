package com.hiosdra.hreader.ui.article

import android.text.Html
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.hiosdra.hreader.data.model.isRead
import com.hiosdra.hreader.navigation.Routes
import com.hiosdra.hreader.ui.components.OfflineAwareImage
import com.hiosdra.hreader.ui.theme.sectionCardColors

@Composable
fun ArticleRow(
    entry: Entry,
    navController: NavController,
    articleIds: List<Long>,
    articleIndex: Int,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val checked = entry.isRead
    val openArticle = { navController.navigate(Routes.article(articleIds, articleIndex)) }

    val contentAlpha by animateFloatAsState(targetValue = if (checked) 0.55f else 1f, label = "alpha")
    val titleWeight = if (checked) FontWeight.Normal else FontWeight.SemiBold
    val indicatorColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "indicator"
    )

    Card(
        onClick = openArticle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
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
                                    .clip(RoundedCornerShape(50))
                                    .background(indicatorColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // The feed name yields space instead of taking all of it. Unweighted it
                            // was measured first, and against a long name in a row narrowed by a
                            // thumbnail the time was left a single character wide, one digit per line.
                            Text(
                                text = entry.feed.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = TIME_FORMATTER.format(entry.publishedAt),
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
                        val preview = entry.content?.let { raw -> extractTextPreview(raw) }.orEmpty()
                        if (preview.isNotBlank()) {
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
                    val imageUrl = entry.enclosures.firstOrNull { it.isImage }?.url
                    if (imageUrl != null) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Fills the square it was given: at fillMaxWidth the height followed the
                            // source aspect ratio, so a portrait photo stood taller than its slot.
                            OfflineAwareImage(
                                entryId = entry.id,
                                imageUrl = imageUrl,
                                contentDescription = "Article image",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.25f))
                                        )
                                    )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onCheckedChange(entry.id, it) }
                )
            }
        }
    }
}

private fun extractTextPreview(html: String): String {
    val cleanedHtml = html
        .replace(Regex("(?is)<(script|style)[^>]*?>.*?</\\1>"), " ")
        .replace(Regex("(?is)<img[^>]*?>"), " ")
        .replace(Regex("(?is)<svg[^>]*?>.*?</svg>"), " ")
        .replace(Regex("(?is)<(video|source|picture)[^>]*?>.*?</\\1>"), " ")

    return Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace('\uFFFC', ' ')
        .lines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
