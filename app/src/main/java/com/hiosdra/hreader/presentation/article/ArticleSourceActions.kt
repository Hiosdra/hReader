package com.hiosdra.hreader.presentation.article

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArticleSourceActions(
    defaultPaywallBypassMethod: PaywallBypassMethod,
    isOnline: Boolean,
    canUsePaywallBypass: Boolean,
    onOpenInChrome: () -> Unit,
    onBypassPaywall: (PaywallBypassMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val openOriginalDescription = stringResource(R.string.article_open_original_in_chrome)
    val externalActionOfflineDescription = stringResource(
        R.string.article_external_actions_requires_connection
    )
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenInChrome,
                enabled = isOnline,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics {
                    contentDescription = openOriginalDescription
                    if (!isOnline) stateDescription = externalActionOfflineDescription
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chrome_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.article_original_short))
            }
            if (canUsePaywallBypass) {
                PaywallBypassSplitButton(
                    defaultPaywallBypassMethod = defaultPaywallBypassMethod,
                    isOnline = isOnline,
                    onBypassPaywall = onBypassPaywall
                )
            }
        }
        if (!isOnline) {
            Text(
                text = externalActionOfflineDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun PaywallBypassSplitButton(
    defaultPaywallBypassMethod: PaywallBypassMethod,
    isOnline: Boolean,
    onBypassPaywall: (PaywallBypassMethod) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val defaultMethodName = stringResource(
        paywallBypassMethodNameRes(defaultPaywallBypassMethod)
    )
    val defaultActionLabel = stringResource(
        R.string.article_open_through_paywall_service,
        defaultMethodName
    )
    val chooseServiceDescription = stringResource(R.string.article_choose_paywall_service)
    val externalActionOfflineDescription = stringResource(
        R.string.article_external_actions_requires_connection
    )
    val shape = MaterialTheme.shapes.small

    Surface(
        modifier = Modifier.clip(shape),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onBypassPaywall(defaultPaywallBypassMethod) },
                enabled = isOnline,
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                modifier = Modifier.semantics {
                    contentDescription = defaultActionLabel
                    if (!isOnline) stateDescription = externalActionOfflineDescription
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = defaultActionLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = isOnline,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = chooseServiceDescription
                            if (!isOnline) stateDescription = externalActionOfflineDescription
                        }
                ) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    PaywallBypassMethod.entries.forEach { method ->
                        val methodName = stringResource(paywallBypassMethodNameRes(method))
                        DropdownMenuItem(
                            text = { Text(methodName) },
                            onClick = {
                                menuExpanded = false
                                onBypassPaywall(method)
                            },
                            enabled = isOnline,
                            leadingIcon = if (method == defaultPaywallBypassMethod) {
                                {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallBypassMethodPicker(
    defaultPaywallBypassMethod: PaywallBypassMethod,
    onSelect: (PaywallBypassMethod) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.article_choose_paywall_service),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            PaywallBypassMethod.entries.forEach { method ->
                val methodName = stringResource(paywallBypassMethodNameRes(method))
                ListItem(
                    headlineContent = { Text(methodName) },
                    supportingContent = {
                        Text(
                            text = method.host,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        if (method == defaultPaywallBypassMethod) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(method) }
                )
            }
        }
    }
}

@StringRes
internal fun paywallBypassMethodNameRes(method: PaywallBypassMethod): Int = when (method) {
    PaywallBypassMethod.SMRY_AI -> R.string.paywall_smry_ai
    PaywallBypassMethod.REMOVE_PAYWALL -> R.string.paywall_remove_paywall
    PaywallBypassMethod.REMOVE_PAYWALLS -> R.string.paywall_remove_paywalls
    PaywallBypassMethod.PAYWALL_BUSTER -> R.string.paywall_paywall_buster
    PaywallBypassMethod.ARCHIVE_PH -> R.string.paywall_archive_ph
    PaywallBypassMethod.WAYBACK_MACHINE -> R.string.paywall_wayback_machine
    PaywallBypassMethod.ARCHIVE_BUTTONS -> R.string.paywall_archive_buttons
    PaywallBypassMethod.BYPASS_PAYWALL_READER -> R.string.paywall_bypass_reader
}
