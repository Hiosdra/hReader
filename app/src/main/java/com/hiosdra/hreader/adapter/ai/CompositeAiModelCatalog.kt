package com.hiosdra.hreader.adapter.ai

import android.content.Context
import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaModelManager
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.util.runCatchingCancellable

class CompositeAiModelCatalog(
    private val context: Context,
    private val openRouter: AiModelCatalog,
    private val gemma: GemmaModelManager,
    private val preferences: AiPreferences
) : AiModelCatalog {
    override suspend fun getModels(forceRefresh: Boolean): List<AiModel> = buildList {
        add(Gemma4E2bModel.descriptor(context))
        addAll(
            runCatchingCancellable { openRouter.getModels(forceRefresh) }
                .getOrDefault(emptyList())
                .filterNot { it.id == Gemma4E2bModel.MODEL_ID }
        )
    }

    override suspend fun checkSelectedModel(): SelectedModelStatus {
        val selectedId = preferences.getAiModelId()
        if (selectedId == Gemma4E2bModel.MODEL_ID) {
            return if (gemma.isInstalled()) {
                SelectedModelStatus.Available
            } else {
                SelectedModelStatus.Unavailable(selectedId)
            }
        }
        return openRouter.checkSelectedModel()
    }
}
