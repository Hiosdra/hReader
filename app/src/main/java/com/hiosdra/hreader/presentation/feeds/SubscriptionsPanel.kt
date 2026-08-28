package com.hiosdra.hreader.presentation.feeds

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.service.displayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.text.Charsets.UTF_8

/** Exporters disagree on the OPML media type, so the picker cannot be narrowed to one. */
private const val OPML_MIME_TYPE = "text/x-opml"
private const val MAX_OPML_BYTES = 100L * 1024L * 1024L

@Composable
fun SubscriptionsPanel(
    selectedFeedId: Long?,
    visible: Boolean,
    onSelectFeed: (Long?) -> Unit,
    onFeedDetails: (Long) -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedsViewModel,
    networkStatus: NetworkStatus
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by networkStatus.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opmlTitle = stringResource(R.string.feeds_opml_title)
    val opmlFilename = stringResource(R.string.feeds_opml_filename)

    var feedPendingRename by remember { mutableStateOf<Feed?>(null) }
    var feedPendingDeletion by remember { mutableStateOf<Feed?>(null) }

    LaunchedEffect(visible) {
        if (visible) viewModel.reload()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val xml = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    context.contentResolver.openInputStream(uri)?.use { it.readBoundedText(MAX_OPML_BYTES) }
                }.getOrNull()
            }
            if (xml == null) viewModel.reportUnreadableFile() else viewModel.importOpml(xml)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(OPML_MIME_TYPE)
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { viewModel.exportOpmlTo(opmlTitle) { opml -> writeTo(context, uri, opml) } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.feeds_title), style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = onAddFeed, enabled = isOnline) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.feeds_add_subscription))
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feeds_import_opml)) },
                            enabled = isOnline,
                            onClick = {
                                menuOpen = false
                                // Any type: exporters write OPML as text/xml, application/xml or
                                // plain octet-stream, and narrowing the picker hides most of them.
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feeds_export_opml)) },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch(opmlFilename)
                            }
                        )
                    }
                }
            }
        }
        if (uiState.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // A drawer has no scaffold to hang a snackbar on, so what an import or an unsubscribe did
        // is said in place, where the list it changed is.
        uiState.message?.let { message ->
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        if (!isOnline) {
            Text(
                text = stringResource(R.string.feeds_offline_changes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        when {
            uiState.isLoading && uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(text = uiState.error?.resolve().orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.reload() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                        OutlinedButton(onClick = onAddFeed, enabled = isOnline, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.feeds_add_subscription))
                        }
                    }
                }
            }
            uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.feeds_no_subscriptions), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAddFeed, enabled = isOnline, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.feeds_subscribe_first))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = isOnline,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(stringResource(R.string.feeds_import_opml)) }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.feeds_search_subscriptions)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.feeds_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        PanelRow(
                            title = stringResource(R.string.main_all_articles),
                            unreadCount = uiState.unreadCounts.values.sum(),
                            selected = selectedFeedId == null,
                            onClick = { onSelectFeed(null) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (uiState.filteredFeeds.isEmpty()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    stringResource(R.string.feeds_no_matches, uiState.searchQuery),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.padding(top = 12.dp)
                                ) { Text(stringResource(R.string.feeds_clear_search)) }
                            }
                        }
                    }
                    items(uiState.filteredFeeds, key = { it.id }) { feed ->
                        PanelRow(
                            title = feed.title,
                            subtitle = feed.siteUrl,
                            unreadCount = uiState.unreadCounts[feed.id] ?: 0,
                            selected = feed.id == selectedFeedId,
                            onClick = { onSelectFeed(feed.id) },
                            onDetailsClick = { onFeedDetails(feed.id) },
                            onRename = { feedPendingRename = feed },
                            onUnsubscribe = { feedPendingDeletion = feed }
                        )
                    }
                }
            }
        }
    }

    // Unsubscribing takes every article in the feed with it and no backend keeps a bin, so this is
    // one of the few places a confirmation earns the interruption.
    feedPendingDeletion?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedPendingDeletion = null },
            title = { Text(stringResource(R.string.feeds_unsubscribe_title, feed.title)) },
            text = { Text(stringResource(R.string.feeds_unsubscribe_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFeed(feed.id)
                    feedPendingDeletion = null
                }) { Text(stringResource(R.string.action_unsubscribe)) }
            },
            dismissButton = {
                TextButton(onClick = { feedPendingDeletion = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    feedPendingRename?.let { feed ->
        RenameFeedDialog(
            feed = feed,
            onConfirm = { title ->
                viewModel.renameFeed(feed.id, title)
                feedPendingRename = null
            },
            onDismiss = { feedPendingRename = null }
        )
    }
}

@Composable
private fun RenameFeedDialog(feed: Feed, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(feed.id) { mutableStateOf(feed.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.feeds_name)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private suspend fun writeTo(context: android.content.Context, uri: Uri, opml: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatchingCancellable {
            context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
        }.isSuccess
    }

private fun java.io.InputStream.readBoundedText(maxBytes: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("OPML file exceeds the ${maxBytes}-byte limit")
        output.write(buffer, 0, count)
    }
    return output.toByteArray().toString(UTF_8)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadCount: Int = 0,
    onDetailsClick: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onUnsubscribe: (() -> Unit)? = null
) {
    // Secondary feed actions stay behind one menu so the row keeps its reading priority.
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (onRename != null || onUnsubscribe != null) menuOpen = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (unreadCount > 0) {
                    Text(
                        text = stringResource(R.string.feeds_unread_count, unreadCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            subtitle?.let {
                Text(
                    text = displayUrl(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            if (onDetailsClick != null || onRename != null || onUnsubscribe != null) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.feeds_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                onDetailsClick?.let { details ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feeds_details)) },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            details()
                        }
                    )
                }
                onRename?.let { rename ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            rename()
                        }
                    )
                }
                onUnsubscribe?.let { unsubscribe ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_unsubscribe)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            unsubscribe()
                        }
                    )
                }
            }
        }
    }
}
