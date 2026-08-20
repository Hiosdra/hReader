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
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun ReadingSettingsSection(
    bionicReadingEnabled: Boolean,
    onBionicReadingChange: (Boolean) -> Unit,
    selectedTtsModel: TtsModel,
    selectedBypassMethod: PaywallBypassMethod,
    onOpenTts: () -> Unit,
    onOpenBypass: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_reading_experience),
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
                title = stringResource(R.string.settings_bionic_reading),
                description = stringResource(R.string.settings_bionic_reading_description),
                checked = bionicReadingEnabled,
                onCheckedChange = onBionicReadingChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingRow(
                title = stringResource(R.string.tts_read_aloud),
                value = stringResource(selectedTtsModel.displayNameRes),
                onClick = onOpenTts
            )
            SettingRow(
                title = stringResource(R.string.settings_bypass_service),
                value = stringResource(selectedBypassMethod.displayNameRes),
                supportingText = selectedBypassMethod.host,
                onClick = onOpenBypass
            )
        }
    }
}
