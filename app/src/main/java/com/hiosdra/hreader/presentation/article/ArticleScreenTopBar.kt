package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance

internal data class ArticleScreenTopBarState(
    val entryUrl: String?,
    val feedTitle: String?,
    val listPosition: Int,
    val listSize: Int,
    val isWebViewMode: Boolean,
    val canUseWebView: Boolean,
    val isRead: Boolean,
    val textScale: Float,
    val ttsContentState: ArticleTtsContentState?,
    val isTtsActive: Boolean,
    val isOnline: Boolean,
    val defaultPaywallBypassMethod: PaywallBypassMethod?,
    val canUsePaywallBypass: Boolean,
    val displayedProvenance: ArticleContentProvenance?
)

internal data class ArticleScreenTopBarActions(
    val onDecreaseTextScale: () -> Unit,
    val onResetTextScale: () -> Unit,
    val onIncreaseTextScale: () -> Unit,
    val onToggleRead: () -> Unit,
    val onBack: () -> Unit,
    val onToggleWebView: () -> Unit,
    val onShare: () -> Unit,
    val onInvokeTts: () -> Unit,
    val onOpenInChrome: () -> Unit,
    val onBypassPaywall: (PaywallBypassMethod) -> Unit,
    val onOpenPaywallMethodPicker: () -> Unit,
    val onSwitchToFeed: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleScreenTopBar(
    state: ArticleScreenTopBarState,
    actions: ArticleScreenTopBarActions
) {
    Column {
        ArticleTopBar(
            entryUrl = state.entryUrl,
            feedTitle = state.feedTitle,
            listPosition = state.listPosition,
            listSize = state.listSize,
            isWebViewMode = state.isWebViewMode,
            canUseWebView = state.canUseWebView,
            isRead = state.isRead,
            textScale = state.textScale,
            onDecreaseTextScale = actions.onDecreaseTextScale,
            onResetTextScale = actions.onResetTextScale,
            onIncreaseTextScale = actions.onIncreaseTextScale,
            onToggleRead = actions.onToggleRead,
            onBack = actions.onBack,
            onToggleWebView = actions.onToggleWebView,
            onShare = actions.onShare,
            ttsContentState = state.ttsContentState,
            isTtsActive = state.isTtsActive,
            onInvokeTts = actions.onInvokeTts,
            isOnline = state.isOnline,
            defaultPaywallBypassMethod = state.defaultPaywallBypassMethod,
            canUsePaywallBypass = state.canUsePaywallBypass,
            onOpenInChrome = actions.onOpenInChrome,
            onBypassPaywall = actions.onBypassPaywall,
            onOpenPaywallMethodPicker = actions.onOpenPaywallMethodPicker
        )
        if (state.isWebViewMode && state.displayedProvenance != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArticleContentProvenanceStatus(
                    provenance = state.displayedProvenance,
                    onSwitchToFeed = actions.onSwitchToFeed
                )
            }
        }
    }
}
