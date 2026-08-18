package com.hiosdra.hreader.adapter.ai.gemma

import android.content.Context
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.AiProvider

object Gemma4E2bModel {
    const val MODEL_ID = AiModel.GEMMA_4_E2B_ID
    const val MODEL_SIZE_BYTES = 2_588_147_712L
    const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

    private const val REVISION = "6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "$REVISION/gemma-4-E2B-it.litertlm"

    fun descriptor(context: Context) = AiModel(
        id = MODEL_ID,
        displayName = context.getString(R.string.ai_gemma_model_name),
        description = context.getString(R.string.ai_gemma_model_description),
        contextLength = 128_000,
        isFree = true,
        provider = AiProvider.GEMMA_LOCAL
    )
}
