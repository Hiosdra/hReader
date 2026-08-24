package com.hiosdra.hreader.presentation.settings

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.tts.TtsModel

@get:StringRes
internal val PaywallBypassMethod.displayNameRes: Int
    get() = when (this) {
        PaywallBypassMethod.SMRY_AI -> R.string.paywall_smry_ai
        PaywallBypassMethod.REMOVE_PAYWALL -> R.string.paywall_remove_paywall
        PaywallBypassMethod.REMOVE_PAYWALLS -> R.string.paywall_remove_paywalls
        PaywallBypassMethod.PAYWALL_BUSTER -> R.string.paywall_paywall_buster
        PaywallBypassMethod.ARCHIVE_PH -> R.string.paywall_archive_ph
        PaywallBypassMethod.WAYBACK_MACHINE -> R.string.paywall_wayback_machine
        PaywallBypassMethod.ARCHIVE_BUTTONS -> R.string.paywall_archive_buttons
        PaywallBypassMethod.BYPASS_PAYWALL_READER -> R.string.paywall_bypass_reader
    }

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
        TtsModel.ANDROID -> R.string.tts_model_android_name
    }

@get:StringRes
internal val TtsModel.descriptionRes: Int
    get() = when (this) {
        TtsModel.SUPERTONIC -> R.string.tts_model_supertonic_description
        TtsModel.KOKORO -> R.string.tts_model_kokoro_description
        TtsModel.GOSIA -> R.string.tts_model_gosia_description
        TtsModel.PIPER_BASS_HIGH -> R.string.tts_model_piper_high_description
        TtsModel.PIPER_DARKMAN_MEDIUM,
        TtsModel.PIPER_JARVIS_MEDIUM,
        TtsModel.PIPER_JUSTYNA_MEDIUM,
        TtsModel.PIPER_MC_SPEECH_MEDIUM,
        TtsModel.PIPER_MESKI_MEDIUM,
        TtsModel.PIPER_ZENSKI_MEDIUM -> R.string.tts_model_piper_medium_description
        TtsModel.ANDROID -> R.string.tts_model_android_description
    }
