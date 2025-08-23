package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod
import com.hiosdra.hreader.data.preferences.PreferencesManager
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    preferencesManager: PreferencesManager = koinInject()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            var selectedBypassMethod by remember { mutableStateOf(preferencesManager.getPaywallBypassMethod()) }
            var bionicReadingEnabled by remember { mutableStateOf(preferencesManager.getBionicReadingEnabled()) }
            val onToggleBionicReading: (Boolean) -> Unit = { enabled ->
                bionicReadingEnabled = enabled
                preferencesManager.setBionicReadingEnabled(enabled)
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Reading Experience",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleBionicReading(!bionicReadingEnabled) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bionic Reading",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Highlight portions of words for faster reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = bionicReadingEnabled,
                            onCheckedChange = onToggleBionicReading
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Paywall Bypass Method",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PaywallBypassMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBypassMethod = method
                                    preferencesManager.setPaywallBypassMethod(method)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selectedBypassMethod == method,
                                onClick = {
                                    selectedBypassMethod = method
                                    preferencesManager.setPaywallBypassMethod(method)
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = method.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = method.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
