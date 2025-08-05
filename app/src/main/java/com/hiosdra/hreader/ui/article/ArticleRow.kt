package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.ui.theme.AuthorAccent
import com.hiosdra.hreader.ui.theme.DateSubtle
import com.hiosdra.hreader.ui.theme.ReadStatusGreen
import com.hiosdra.hreader.ui.theme.UnreadStatusBlue

@Composable
fun ArticleRow(
    entry: Entry,
    navController: NavController,
    articleIds: List<Long>,
    articleIndex: Int,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val isRead = entry.status == "read"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable {
                navController.navigate("article/${articleIds.joinToString(",")}/$articleIndex")
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Author and timestamp row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = entry.author ?: "Unknown Source",
                        style = MaterialTheme.typography.labelMedium,
                        color = AuthorAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • ${entry.publishedAt.substring(11, 16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DateSubtle
                    )
                }
                
                // Article title
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (isRead) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Article preview
                val preview = entry.content?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: ""
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.size(12.dp))
            
            // Image and checkbox column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Article image
                val imageUrl = entry.enclosures?.firstOrNull {
                    it.mimeType?.startsWith("image/") == true
                }?.url
                
                if (imageUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(imageUrl),
                            contentDescription = "Article image",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                
                // Read status checkbox
                Checkbox(
                    checked = isRead,
                    onCheckedChange = { onCheckedChange(entry.id, it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ReadStatusGreen,
                        uncheckedColor = UnreadStatusBlue,
                        checkmarkColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}
