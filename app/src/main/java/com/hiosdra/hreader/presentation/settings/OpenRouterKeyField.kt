package com.hiosdra.hreader.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R

@Composable
fun OpenRouterKeyField(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    SecretSettingRow(
        title = stringResource(R.string.openrouter_api_key),
        value = apiKey,
        supportingText = stringResource(R.string.openrouter_api_key_description),
        placeholder = stringResource(R.string.openrouter_api_key_placeholder),
        onSave = onApiKeyChange
    )
}
