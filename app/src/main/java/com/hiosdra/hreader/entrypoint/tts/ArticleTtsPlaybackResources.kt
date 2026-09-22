package com.hiosdra.hreader.entrypoint.tts

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

@get:StringRes
internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.KOKORO_V1_0 -> R.string.tts_model_kokoro_v1_0_name
        TtsModel.COQUI_PL_MAI_FEMALE -> R.string.tts_model_coqui_pl_mai_female_name
        TtsModel.KITTEN_MINI -> R.string.tts_model_kitten_mini_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }
