package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.data.ai.AiModel

private const val SHEET_HEIGHT_FRACTION = 0.9f

/**
 * A sheet rather than a dropdown: several hundred models with a search field, a filter and a
 * reload that can fail need room and a scroll of their own. Search and filter stay pinned at the
 * top so they are still reachable once the list is scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelSheet(
    state: AiModelsUiState,
    onSearchQueryChange: (String) -> Unit,
    onFreeOnlyChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            Text(
                text = "AI model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            AiModelSearchBar(
                state = state,
                onSearchQueryChange = onSearchQueryChange,
                onFreeOnlyChange = onFreeOnlyChange,
                onReload = onReload,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            if (state.visibleModels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.models.isEmpty()) {
                            "No models loaded from OpenRouter"
                        } else {
                            "Nothing matches that search"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.visibleModels, key = { it.id }) { model ->
                        AiModelRow(
                            model = model,
                            isSelected = state.selectedModelId == model.id,
                            onClick = { onModelSelected(model) }
                        )
                    }
                }
            }
        }
    }
}
