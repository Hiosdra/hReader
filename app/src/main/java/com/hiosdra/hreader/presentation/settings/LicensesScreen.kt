package com.hiosdra.hreader.presentation.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LicenseDocument(val path: String)

private val LICENSE_DOCUMENTS = listOf(
    LicenseDocument("licenses/README.md"),
    LicenseDocument("licenses/models/README.md"),
    LicenseDocument("licenses/models/gemma-4/NOTICE.md"),
    LicenseDocument("licenses/models/gemma-4/LICENSE"),
    LicenseDocument("licenses/models/gemma-4/MODEL_CARD.md"),
    LicenseDocument("licenses/models/supertonic-3/NOTICE.md"),
    LicenseDocument("licenses/models/supertonic-3/LICENSE"),
    LicenseDocument("licenses/models/coqui-pl-mai-female/NOTICE.md"),
    LicenseDocument("licenses/models/coqui-pl-mai-female/LICENSE"),
    LicenseDocument("licenses/models/kokoro/NOTICE.md"),
    LicenseDocument("licenses/models/kokoro/LICENSE"),
    LicenseDocument("licenses/models/kitten/NOTICE.md"),
    LicenseDocument("licenses/models/kitten/LICENSE"),
    LicenseDocument("licenses/models/helpers/espeak-ng/NOTICE.md"),
    LicenseDocument("licenses/models/helpers/espeak-ng/COPYING"),
    LicenseDocument("licenses/models/helpers/cppjieba/NOTICE.md"),
    LicenseDocument("licenses/models/helpers/cppjieba/LICENSE"),
    LicenseDocument("licenses/litert/LICENSE"),
    LicenseDocument("licenses/litert/THIRD_PARTY_NOTICE.txt"),
    LicenseDocument("licenses/sherpa-onnx/LICENSE"),
    LicenseDocument("licenses/sherpa-onnx/onnxruntime/LICENSE"),
    LicenseDocument("licenses/sherpa-onnx/onnxruntime/ThirdPartyNotices.txt"),
    LicenseDocument("tts/NOTICE")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(navController: NavController) {
    var selectedDocument by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.licenses_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(LICENSE_DOCUMENTS, key = { it.path }) { document ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDocument = document.path }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = document.path,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.licenses_document_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    selectedDocument?.let { path ->
        LicenseDocumentDialog(
            path = path,
            onDismiss = { selectedDocument = null }
        )
    }
}

@Composable
private fun LicenseDocumentDialog(
    path: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var content by remember(path) { mutableStateOf<String?>(null) }
    var loadError by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        runCatching {
            withContext(Dispatchers.IO) {
                context.openAssetDocument(path)
            }
        }.onSuccess { content = it }
            .onFailure { loadError = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(path) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 560.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loadError -> Text(stringResource(R.string.licenses_load_error))
                    content == null -> CircularProgressIndicator()
                    else -> {
                        val lines = remember(content) { content.orEmpty().lineSequence().toList() }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(lines) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

private fun Context.openAssetDocument(path: String): String =
    assets.open(path).bufferedReader().use { it.readText() }
