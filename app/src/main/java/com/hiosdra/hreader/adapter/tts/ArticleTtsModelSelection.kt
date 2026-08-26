package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsLanguages
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus

internal fun resolveArticleTtsModel(
    modelOverride: TtsModel?,
    settingsModel: TtsModel,
    language: String,
    statuses: Map<TtsModel, TtsModelStatus>,
    supportsArm64: Boolean
): TtsModel {
    val requestedModel = modelOverride ?: settingsModel
    return if (
        supportsArm64 &&
            statuses[requestedModel] == TtsModelStatus.Available &&
            TtsLanguages.isCompatible(requestedModel, language)
    ) {
        requestedModel
    } else {
        TtsModel.ANDROID
    }
}
