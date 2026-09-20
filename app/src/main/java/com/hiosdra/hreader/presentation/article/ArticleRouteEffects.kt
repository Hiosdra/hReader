package com.hiosdra.hreader.presentation.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.domain.service.cleanUrl
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

sealed interface ArticleRouteEffect {
    data class OpenBrowser(val url: String) : ArticleRouteEffect

    data class OpenPaywall(
        val url: String,
        val method: PaywallBypassMethod
    ) : ArticleRouteEffect

    data class ShareArticle(
        val title: String,
        val url: String
    ) : ArticleRouteEffect

    data class CopyText(
        val label: String,
        val text: String,
        val followUpMessage: String? = null
    ) : ArticleRouteEffect

    data class ShowToast(val message: String) : ArticleRouteEffect

    data class DownloadImage(
        val url: String,
        val resultMessage: String,
        val failureMessage: String,
        val retryActionLabel: String,
        val onRetry: () -> Unit
    ) : ArticleRouteEffect

    data class ShareImage(
        val title: String,
        val url: String,
        val preparingMessage: String,
        val failureMessage: String,
        val retryActionLabel: String,
        val onRetry: () -> Unit
    ) : ArticleRouteEffect
}

@Composable
internal fun ArticleRouteEffectHost(
    effects: Flow<ArticleRouteEffect>,
    context: Context,
    paywallBypassService: PaywallBypass,
    articleImageSharer: ArticleImageSharer,
    articleImageDownloader: ArticleImageDownloader,
    onFeedback: (FeedbackRequest) -> Unit
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ArticleRouteEffect.OpenBrowser -> {
                    if (effect.url.isNotBlank()) {
                        openChromeCustomTab(context, cleanUrl(effect.url))
                    }
                }
                is ArticleRouteEffect.OpenPaywall -> {
                    if (effect.url.isNotBlank()) {
                        val bypassUrl = paywallBypassService.getBypassUrl(effect.url, effect.method)
                        openChromeCustomTab(context, bypassUrl)
                    }
                }
                is ArticleRouteEffect.ShareArticle -> shareArticle(context, effect.title, effect.url)
                is ArticleRouteEffect.CopyText -> {
                    copyTextToClipboard(context, effect.label, effect.text)
                    onFeedback(
                        FeedbackRequest(
                            message = effect.followUpMessage
                                ?: context.getString(com.hiosdra.hreader.R.string.article_copied)
                        )
                    )
                }
                is ArticleRouteEffect.ShowToast -> onFeedback(FeedbackRequest(message = effect.message))
                is ArticleRouteEffect.DownloadImage -> {
                    val downloaded = articleImageDownloader.download(effect.url)
                    onFeedback(
                        if (downloaded) {
                            FeedbackRequest(message = effect.resultMessage)
                        } else {
                            FeedbackRequest(
                                message = effect.failureMessage,
                                kind = FeedbackKind.RECOVERABLE_ERROR,
                                actionLabel = effect.retryActionLabel,
                                onAction = effect.onRetry
                            )
                        }
                    )
                }
                is ArticleRouteEffect.ShareImage -> {
                    onFeedback(FeedbackRequest(message = effect.preparingMessage))
                    if (!articleImageSharer.share(effect.title, effect.url)) {
                        onFeedback(
                            FeedbackRequest(
                                message = effect.failureMessage,
                                kind = FeedbackKind.RECOVERABLE_ERROR,
                                actionLabel = effect.retryActionLabel,
                                onAction = effect.onRetry
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun shareArticle(context: Context, title: String, url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n${cleanUrl(url)}")
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
