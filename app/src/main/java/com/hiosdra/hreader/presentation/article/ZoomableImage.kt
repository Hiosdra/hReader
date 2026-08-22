package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hiosdra.hreader.presentation.components.OfflineAwareZoomableImage

@Composable
fun ZoomableImage(entryId: Long, url: String, onDismiss: () -> Unit) {
    Surface(onClick = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            OfflineAwareZoomableImage(
                entryId = entryId,
                imageUrl = url,
                contentDescription = null,
                onClick = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
