package com.hiosdra.hreader.ui.feeds

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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.util.displayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

/** Exporters disagree on the OPML media type, so the picker cannot be narrowed to one. */
private const val OPML_MIME_TYPE = "text/x-opml"

@Composable
fun SubscriptionsPanel(
    selectedFeedId: Long?,
    visible: Boolean,
    onSelectFeed: (Long?) -> Unit,
    onFeedDetails: (Long) -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val koin = getKoin()
    val networkMonitor = remember { koin.get<NetworkMonitor>() }
    val isOnline by networkMonitor.isOnline.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (xml == null) viewModel.reportUnreadableFile() else viewModel.importOpml(xml)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(OPML_MIME_TYPE)
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { viewModel.exportOpmlTo { opml -> writeTo(context, uri, opml) } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Subscriptions", style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = onAddFeed, enabled = isOnline) {
                    Icon(Icons.Filled.Add, contentDescription = "Add subscription")
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import OPML") },
                            enabled = isOnline,
                            onClick = {
                                menuOpen = false
                                // Any type: exporters write OPML as text/xml, application/xml or
                                // plain octet-stream, and narrowing the picker hides most of them.
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export OPML") },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("hreader-subscriptions.opml")
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
                text = message,
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
                text = "Offline – subscription changes need a connection",
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
                        Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.reload() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onAddFeed, enabled = isOnline, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Add subscription")
                        }
                    }
                }
            }
            uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No subscriptions yet", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAddFeed, enabled = isOnline, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Add your first feed")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = isOnline,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Import from OPML") }
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
                    placeholder = { Text("Search subscriptions...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        PanelRow(
                            title = "All items",
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
                                    "No matches for '${uiState.searchQuery}'",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.padding(top = 12.dp)
                                ) { Text("Clear search") }
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
            title = { Text("Unsubscribe from ${feed.title}?") },
            text = { Text("The subscription and every article downloaded from it are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFeed(feed.id)
                    feedPendingDeletion = null
                }) { Text("Unsubscribe") }
            },
            dismissButton = {
                TextButton(onClick = { feedPendingDeletion = null }) { Text("Cancel") }
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
        title = { Text("Rename feed") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Title") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private suspend fun writeTo(context: android.content.Context, uri: Uri, opml: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
        }.isSuccess
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
    // Row actions stay in one menu so the feed remains easy to scan and the details action does not
    // compete with the unread count for space.
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
                        text = "  ($unreadCount)",
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
                        contentDescription = "Feed actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                onDetailsClick?.let { details ->
                    DropdownMenuItem(
                        text = { Text("Feed details") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            details()
                        }
                    )
                }
                onRename?.let { rename ->
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            rename()
                        }
                    )
                }
                onUnsubscribe?.let { unsubscribe ->
                    DropdownMenuItem(
                        text = { Text("Unsubscribe") },
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
