package com.hiosdra.hreader.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.isRead
import com.hiosdra.hreader.navigation.Routes
import com.hiosdra.hreader.ui.article.ArticleListGrouped
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    feedId: Long? = null,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(feedId) {
        if (feedId != null) viewModel.setFeed(feedId) else viewModel.clearFeed()
    }

    val unreadCount = uiState.entries.count { !it.isRead }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val searchActive = remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                uiState.unavailableAiModelId?.let { modelId ->
                    AiModelUnavailableBanner(
                        modelId = modelId,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onDismiss = viewModel::dismissAiModelWarning
                    )
                }
                TopAppBar(
                    title = {
                        Text(
                            (uiState.feedTitle ?: "All Items") + if (unreadCount > 0) "  •  $unreadCount" else "",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (feedId == null) {
                            IconButton(
                                onClick = { navController.navigate(Routes.FEEDS) },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = "Feeds",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { searchActive.value = !searchActive.value },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                if (searchActive.value) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { if (!uiState.isRefreshing) viewModel.refreshFromNetwork() },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        val expanded = remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded.value = true },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = expanded.value,
                            onDismissRequest = { expanded.value = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
                                onClick = {
                                    expanded.value = false
                                    navController.navigate(Routes.SETTINGS)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(visible = searchActive.value) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 4.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search articles") },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val showDialog = remember { mutableStateOf(false) }
            val showFab = !uiState.isLoading && unreadCount > 0 && !showDialog.value
            val alpha by animateFloatAsState(targetValue = if (showFab) 1f else 0f, animationSpec = tween(300))
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showDialog.value = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    text = { Text(text = "Mark $unreadCount read") },
                    expanded = unreadCount < 100,
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
            }
            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text("Mark all as read?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start) },
                    text = { Text("Are you sure you want to mark all articles as read?", style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        ElevatedButton(
                            onClick = {
                                showDialog.value = false
                                viewModel.markAllAsRead()
                            },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showDialog.value = false },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) { Text("Cancel") }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else if (uiState.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        ElevatedButton(
                            onClick = { viewModel.refreshFromNetwork() },
                            modifier = Modifier.padding(top = 20.dp)
                        ) { Text("Retry") }
                    } else {
                        Text(
                            text = if (feedId == null) "No articles yet" else "No articles for this feed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                        ElevatedButton(
                            onClick = { if (feedId == null) navController.navigate(Routes.FEEDS) else navController.popBackStack() },
                            modifier = Modifier.padding(top = 20.dp)
                        ) { Text(if (feedId == null) "Browse subscriptions" else "Back to all items") }
                        OutlinedButton(
                            onClick = { navController.navigate(Routes.ADD_FEED) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("Add subscription") }
                        OutlinedButton(
                            onClick = { viewModel.refreshFromNetwork() },
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("Refresh now") }
                    }
                }
            }
        } else {
            ArticleListGrouped(
                entries = uiState.entries,
                navController = navController,
                modifier = Modifier
                    .padding(paddingValues),
                onCheckedChange = { entryId, checked ->
                    viewModel.updateEntryReadStatus(entryId, checked)
                }
            )
        }
    }
}

@Composable
private fun AiModelUnavailableBanner(
    modelId: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "AI model unavailable",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "OpenRouter no longer offers $modelId. Article overviews and credibility scoring will fail until you pick another model.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) { Text("Open settings") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
