package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.GOSIA -> R.string.tts_model_gosia_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }

internal val TtsModel.descriptionRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_description
        TtsModel.KOKORO -> R.string.tts_model_kokoro_description
        TtsModel.GOSIA -> R.string.tts_model_gosia_description
        TtsModel.ANDROID -> R.string.tts_model_android_description
    }
