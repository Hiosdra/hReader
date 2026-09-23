package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelModeSettingsScreen(
    navController: NavController,
    networkStatus: NetworkStatus,
    settingsViewModel: SettingsViewModel
) {
    val offline by settingsViewModel.offline.collectAsStateWithLifecycle()
    val sync by settingsViewModel.sync.collectAsStateWithLifecycle()
    val isOnline by networkStatus.isOnline.collectAsStateWithLifecycle()
    val actions = remember(settingsViewModel) { settingsViewModel.actions.offline }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.travel_mode_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navController::popBackStack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TravelModeSettingsSection(
                    offline = offline,
                    sync = sync,
                    isOnline = isOnline,
                    actions = actions,
                    requestNotificationPermission = requestNotificationPermission
                )
            }
        }
    }
}
