package com.hiosdra.hreader.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.ui.article.ArticleListGrouped
import com.hiosdra.hreader.ui.theme.LocalExtendedColors
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val extendedColors = LocalExtendedColors.current

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "All Items",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigate("feeds") },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Feeds",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshFromNetwork() },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
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
                            .background(extendedColors.cardBackground)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Settings",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            onClick = {
                                expanded.value = false
                                navController.navigate("settings")
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
                )
            )
        },
        floatingActionButton = {
            val showDialog = remember { mutableStateOf(false) }
            AnimatedVisibility(
                visible = !uiState.isLoading && uiState.entries.any { it.status != "read" },
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                FloatingActionButton(
                    onClick = { showDialog.value = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = "Mark all as read",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = {
                        Text(
                            "Mark all as read?",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    text = {
                        Text(
                            "Are you sure you want to mark all articles as read?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    },
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
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showDialog.value = false },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Cancel")
                        }
                    },
                    containerColor = extendedColors.cardBackground,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            ArticleListGrouped(
                entries = uiState.entries,
                navController = navController,
                modifier = Modifier.padding(paddingValues),
                onCheckedChange = { entryId, checked ->
                    viewModel.updateEntryReadStatus(entryId, checked)
                }
            )
        }
    }
}
