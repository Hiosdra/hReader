package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.ai.AiProviderException
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewPrefetchStore
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import kotlinx.coroutines.CancellationException
import java.io.IOException

private const val MAX_RUN_ATTEMPTS = 5
private const val MAX_OVERVIEWS_PER_RUN = 8
private const val MAX_TARGETS_SCANNED_PER_RUN = 64
private const val TARGET_SCAN_BATCH_SIZE = 16
private const val MIN_BATTERY_PERCENT = 80

class ArticleAiOverviewPreloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val targets: ArticleAiOverviewPrefetchStore,
    private val content: ArticleContentStore,
    private val ai: ArticleAiGateway,
    private val overviews: ArticleAiOverviewStore,
    private val aiPreferences: AiPreferences,
    private val errorReporter: ErrorReporter
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!batteryAllowsPreloading()) {
            Log.i(TAG, "Battery is below 80% and the device is not charging")
            return retryOrFinish()
        }

        val modelId = inputData.getString(KEY_AI_MODEL_ID)
            ?.takeIf { it.isNotBlank() }
            ?: aiPreferences.getAiModelId()
        val provider = AiModel.providerFor(modelId)
        if (provider == AiProvider.OPENROUTER && aiPreferences.getOpenRouterApiKey().isBlank()) {
            Log.i(TAG, "Skipping AI overview preload because OpenRouter is not configured")
            return Result.success()
        }

        return try {
            val allowNetworkForContent = provider == AiProvider.OPENROUTER
            var generated = 0
            var scanned = 0
            var offset = 0
            var retryableFailure: Throwable? = null
            var stopScanning = false

            while (generated < MAX_OVERVIEWS_PER_RUN && scanned < MAX_TARGETS_SCANNED_PER_RUN) {
                val batchSize = minOf(TARGET_SCAN_BATCH_SIZE, MAX_TARGETS_SCANNED_PER_RUN - scanned)
                val preloadTargets = targets.getAiOverviewPrefetchTargets(
                    limit = batchSize,
                    offset = offset
                )
                if (preloadTargets.isEmpty()) break
                scanned += preloadTargets.size
                offset += preloadTargets.size

                for (target in preloadTargets) {
                    if (generated >= MAX_OVERVIEWS_PER_RUN) break

                    val articleText = try {
                        content.getArticleContent(
                            entryId = target.id,
                            url = target.url,
                            allowNetwork = allowNetworkForContent
                        )
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        Log.w(TAG, "Could not load content for AI overview ${target.id}", failure)
                        continue
                    }

                    if (articleText.source != ArticleContentSource.FULL || articleText.html.isBlank()) {
                        Log.d(TAG, "Skipping AI overview ${target.id}; full article content is unavailable")
                        continue
                    }
                    if (overviews.get(target.id, articleText.html, modelId) != null) continue

                    val result = ai.generateArticleOverview(
                        title = target.title,
                        content = articleText.html,
                        modelId = modelId
                    )
                    val failure = result.exceptionOrNull()
                    if (failure != null) {
                        if (failure is CancellationException) throw failure
                        if (failure.isRetryableForAi()) {
                            retryableFailure = failure
                            Log.w(TAG, "Temporary AI overview failure for ${target.id}", failure)
                            break
                        }
                        if (failure is GemmaModelNotInstalledException) {
                            Log.i(TAG, "Skipping AI overview preload because Gemma is not installed")
                            stopScanning = true
                            break
                        }
                        if (failure !is EmptyAiContentException && failure !is MissingAiApiKeyException) {
                            Log.w(TAG, "Skipping AI overview ${target.id}", failure)
                        }
                        continue
                    }
                    val overview = result.getOrNull() ?: continue

                    if (overview.isBlank()) continue
                    overviews.save(target.id, articleText.html, modelId, overview)
                    generated++
                }

                if (stopScanning || retryableFailure != null || preloadTargets.size < batchSize) break
            }

            retryableFailure?.let {
                if (runAttemptCount < MAX_RUN_ATTEMPTS) return Result.retry()
                Log.w(TAG, "AI overview preload reached the retry limit", it)
            }
            Log.i(TAG, "AI overview preload completed; generated $generated overviews")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI overview preload failed: ${e.message}", e)
            if (e.isRetryableForAi() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                Result.retry()
            } else {
                if (!e.isRetryableForAi()) errorReporter.captureException(e, COMPONENT)
                Result.success()
            }
        }
    }

    private fun retryOrFinish(): Result =
        if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.success()

    private fun batteryAllowsPreloading(): Boolean {
        val battery = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return isBatteryEligible(
            status = status,
            level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            isPluggedIn = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        )
    }

    companion object {
        private const val TAG = "ArticleAiOverviewPreload"
        private const val COMPONENT = "ai_overview_preload"
    }
}

internal fun isBatteryLevelAllowed(level: Int, scale: Int): Boolean =
    scale > 0 && level >= 0 && level.toLong() * 100 > MIN_BATTERY_PERCENT.toLong() * scale

internal fun isBatteryEligible(
    status: Int,
    level: Int,
    scale: Int,
    isPluggedIn: Boolean = false
): Boolean =
    isPluggedIn ||
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL ||
        isBatteryLevelAllowed(level, scale)

private fun Throwable.isRetryableForAi(): Boolean = when (this) {
    is IOException -> true
    is AiProviderException -> statusCode?.let { it == 429 || it >= 500 } ?: true
    else -> false
}
