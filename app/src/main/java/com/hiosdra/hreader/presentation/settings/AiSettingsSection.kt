package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun AiSettingsSection(
    credibilityScoreEnabled: Boolean,
    onCredibilityScoreChange: (Boolean) -> Unit,
    openRouterApiKey: String,
    onOpenRouterApiKeyChange: (String) -> Unit,
    aiModels: AiModelsUiState,
    onOpenModelPicker: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_ai_features),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column {
            ToggleSettingRow(
                title = stringResource(R.string.settings_show_credibility_chip),
                description = stringResource(R.string.settings_credibility_description),
                checked = credibilityScoreEnabled,
                onCheckedChange = onCredibilityScoreChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (credibilityScoreEnabled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_rating_meaning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_rating_values),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_credibility_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            OpenRouterKeyField(
                apiKey = openRouterApiKey,
                onApiKeyChange = onOpenRouterApiKeyChange,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingRow(
                title = stringResource(R.string.settings_model),
                value = aiModels.selectedModelName,
                supportingText = aiModels.selectedModelId.takeIf { it != aiModels.selectedModelName },
                onClick = onOpenModelPicker
            )
            if (aiModels.selectedModelIsMissing) {
                Text(
                    text = stringResource(R.string.settings_model_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}
