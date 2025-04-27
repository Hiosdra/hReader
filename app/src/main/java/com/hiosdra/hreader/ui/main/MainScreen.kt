package com.hiosdra.hreader.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.hiosdra.hreader.ui.article.ArticleListGrouped
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry =
        remember(navController.currentBackStackEntry) { navController.getBackStackEntry("main") }
    DisposableEffect(navBackStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadEntries()
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)
        onDispose {
            navBackStackEntry.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = {
                    Text("All items", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("feeds") }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Feeds")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadEntries() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    val expanded = remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded.value = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                expanded.value = false
                                navController.navigate("settings")
                            }
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ArticleListGrouped(
                entries = uiState.entries,
                navController = navController,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
