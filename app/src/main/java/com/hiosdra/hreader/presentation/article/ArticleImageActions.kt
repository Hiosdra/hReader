package com.hiosdra.hreader.presentation.article

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hiosdra.hreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageActionsDialog(
    imageUrl: String,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = imageUrl,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_view_image)) },
                modifier = Modifier.clickable(onClick = onView)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_copy_image_url)) },
                modifier = Modifier.clickable(onClick = onCopy)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_download_image)) },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onDownload)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.article_share_image)) },
                modifier = Modifier.clickable(enabled = isOnline, onClick = onShare)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.article_copied), Toast.LENGTH_SHORT).show()
}

internal fun enqueueImageDownload(context: Context, url: String) {
    val started = runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(url.toUri())
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeImageFileName(url))
        dm.enqueue(request)
    }.isSuccess
    val message = context.getString(if (started) R.string.article_downloading else R.string.article_download_failed)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun safeImageFileName(url: String, extension: String = ""): String {
    val segment = runCatching { url.toUri().lastPathSegment }.getOrNull().orEmpty()
    val sanitized = segment
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
        .take(80)
    val base = sanitized.ifBlank { "image_${System.currentTimeMillis()}" }
    return if (extension.isEmpty() || base.endsWith(extension, ignoreCase = true)) base else base + extension
}
