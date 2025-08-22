package com.hiosdra.hreader.ui.article

import android.text.Html
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CheckboxDefaults
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
import coil.compose.rememberAsyncImagePainter
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.ui.theme.LocalExtendedColors

@Composable
fun ArticleRow(
    entry: Entry,
    navController: NavController,
    articleIds: List<Long>,
    articleIndex: Int,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val checked = entry.status == "read"
    val extendedColors = LocalExtendedColors.current
    val openArticle = { navController.navigate("article/${articleIds.joinToString(",")}/$articleIndex") }

    val contentAlpha by animateFloatAsState(targetValue = if (checked) 0.55f else 1f, label = "alpha")
    val titleWeight = if (checked) FontWeight.Normal else FontWeight.SemiBold
    val indicatorColor by animateColorAsState(
        targetValue = if (checked) extendedColors.unchecked.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
        label = "indicator"
    )

    Card(
        onClick = openArticle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = extendedColors.cardBackground)
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
                            Text(
                                text = entry.author?.takeIf { it.isNotBlank() } ?: entry.feed.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = extendedColors.author
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.publishedAt.substring(11, 16),
                                style = MaterialTheme.typography.labelSmall,
                                color = extendedColors.date
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
                                color = extendedColors.author,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val imageUrl = entry.enclosures?.firstOrNull { it.mimeType?.startsWith("image/") == true }?.url
                    if (imageUrl != null) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = "Article image",
                                modifier = Modifier.fillMaxWidth(),
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
                    onCheckedChange = { onCheckedChange(entry.id, it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = extendedColors.checked,
                        uncheckedColor = extendedColors.unchecked
                    )
                )
            }
        }
    }
}

private fun extractTextPreview(html: String): String {
    val noScripts = html.replace(Regex("(?is)<(script|style)[^>]*?>.*?</\\1>"), " ")
    val noImages = noScripts.replace(Regex("(?is)<img[^>]*?>"), " ")
    val noSvgs = noImages.replace(Regex("(?is)<svg[^>]*?>.*?</svg>"), " ")
    val noVideos = noSvgs.replace(Regex("(?is)<(video|source|picture)[^>]*?>.*?</\\1>"), " ")
    val text = Html.fromHtml(noVideos, Html.FROM_HTML_MODE_LEGACY).toString()
    return text
        .replace('\uFFFC', ' ')
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
