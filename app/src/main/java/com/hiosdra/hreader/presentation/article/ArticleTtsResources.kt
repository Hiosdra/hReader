package com.hiosdra.hreader.presentation.article

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

@get:StringRes
internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.GOSIA -> R.string.tts_model_gosia_name
        TtsModel.PIPER_LESSAC_HIGH -> R.string.tts_model_piper_lessac_high_name
        TtsModel.KITTEN_MINI -> R.string.tts_model_kitten_mini_name
        TtsModel.MATCHA_LJSPEECH -> R.string.tts_model_matcha_ljspeech_name
        TtsModel.CHATTERBOX_EXECUTORCH -> R.string.tts_model_chatterbox_executorch_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }
