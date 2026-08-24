package com.hiosdra.hreader.entrypoint.tts

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel

@get:StringRes
internal val TtsModel.displayNameRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_name
        TtsModel.KOKORO -> R.string.tts_model_kokoro_name
        TtsModel.GOSIA -> R.string.tts_model_gosia_name
        TtsModel.PIPER_BASS_HIGH -> R.string.tts_model_piper_bass_high_name
        TtsModel.PIPER_DARKMAN_MEDIUM -> R.string.tts_model_piper_darkman_name
        TtsModel.PIPER_JARVIS_MEDIUM -> R.string.tts_model_piper_jarvis_name
        TtsModel.PIPER_JUSTYNA_MEDIUM -> R.string.tts_model_piper_justyna_name
        TtsModel.PIPER_MC_SPEECH_MEDIUM -> R.string.tts_model_piper_mc_speech_name
        TtsModel.PIPER_MESKI_MEDIUM -> R.string.tts_model_piper_meski_name
        TtsModel.PIPER_ZENSKI_MEDIUM -> R.string.tts_model_piper_zenski_name
        TtsModel.PIPER_LESSAC_HIGH -> R.string.tts_model_piper_lessac_high_name
        TtsModel.KITTEN_MINI -> R.string.tts_model_kitten_mini_name
        TtsModel.MATCHA_LJSPEECH -> R.string.tts_model_matcha_ljspeech_name
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }
