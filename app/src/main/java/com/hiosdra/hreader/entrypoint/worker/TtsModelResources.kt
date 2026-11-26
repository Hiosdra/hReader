package com.hiosdra.hreader.entrypoint.worker

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

@get:StringRes
internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.COQUI_PL_MAI_FEMALE -> R.string.tts_model_coqui_pl_mai_female_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }
