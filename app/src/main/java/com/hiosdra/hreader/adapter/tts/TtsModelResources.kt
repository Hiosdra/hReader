package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.GOSIA -> R.string.tts_model_gosia_name
        TtsModel.PIPER_LESSAC_HIGH -> R.string.tts_model_piper_lessac_high_name
        TtsModel.KITTEN_MINI -> R.string.tts_model_kitten_mini_name
        TtsModel.MATCHA_LJSPEECH -> R.string.tts_model_matcha_ljspeech_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }

internal val TtsModel.descriptionRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_description
        TtsModel.KOKORO -> R.string.tts_model_kokoro_description
        TtsModel.GOSIA -> R.string.tts_model_gosia_description
        TtsModel.PIPER_LESSAC_HIGH -> R.string.tts_model_piper_english_high_description
        TtsModel.KITTEN_MINI -> R.string.tts_model_kitten_description
        TtsModel.MATCHA_LJSPEECH -> R.string.tts_model_matcha_description
        TtsModel.ANDROID -> R.string.tts_model_android_description
    }
