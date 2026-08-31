#include <jni.h>

#include "qwen3_tts_c.h"

#include <algorithm>
#include <exception>
#include <string>

namespace {

struct QwenHandle {
    qwen3_tts_context_t* context = qwen3_tts_init();
    std::string last_error;
};

std::string resultError(const qwen3_tts_result_t& result) {
    return result.error_msg == nullptr || result.error_msg[0] == '\0'
        ? "Qwen3-TTS synthesis failed"
        : result.error_msg;
}

void setExceptionError(QwenHandle* handle, const std::exception& error) {
    if (handle != nullptr) {
        handle->last_error = error.what();
    }
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hiosdra_hreader_adapter_tts_QwenTtsNative_nativeCreate(JNIEnv*, jobject) {
    try {
        auto* handle = new QwenHandle();
        if (handle->context == nullptr) {
            delete handle;
            return 0;
        }
        return reinterpret_cast<jlong>(handle);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hiosdra_hreader_adapter_tts_QwenTtsNative_nativeFree(JNIEnv*, jobject, jlong handle_ptr) {
    auto* handle = reinterpret_cast<QwenHandle*>(handle_ptr);
    if (handle == nullptr) return;
    qwen3_tts_free(handle->context);
    delete handle;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hiosdra_hreader_adapter_tts_QwenTtsNative_nativeLoad(
    JNIEnv* env,
    jobject,
    jlong handle_ptr,
    jstring model_directory,
    jstring model_name,
    jint num_threads
) {
    auto* handle = reinterpret_cast<QwenHandle*>(handle_ptr);
    if (handle == nullptr || handle->context == nullptr || model_directory == nullptr || model_name == nullptr) {
        return JNI_FALSE;
    }

    const char* directory = env->GetStringUTFChars(model_directory, nullptr);
    const char* name = env->GetStringUTFChars(model_name, nullptr);
    if (directory == nullptr || name == nullptr) {
        if (directory != nullptr) env->ReleaseStringUTFChars(model_directory, directory);
        if (name != nullptr) env->ReleaseStringUTFChars(model_name, name);
        handle->last_error = "Could not read Qwen3-TTS model path";
        return JNI_FALSE;
    }

    try {
        handle->last_error.clear();
        const int threads = std::clamp(static_cast<int>(num_threads), 1, 4);
        if (qwen3_tts_set_backend_preference(QWEN3_TTS_BACKEND_CPU) == 0 ||
            qwen3_tts_set_cpu_threads(threads) == 0 ||
            qwen3_tts_load_models_with_name(handle->context, directory, name) == 0) {
            char* native_error = qwen3_tts_get_last_error(handle->context);
            handle->last_error = native_error == nullptr || native_error[0] == '\0'
                ? "Qwen3-TTS model load failed"
                : native_error;
            qwen3_tts_free_string(native_error);
            env->ReleaseStringUTFChars(model_directory, directory);
            env->ReleaseStringUTFChars(model_name, name);
            return JNI_FALSE;
        }
    } catch (const std::exception& error) {
        setExceptionError(handle, error);
        env->ReleaseStringUTFChars(model_directory, directory);
        env->ReleaseStringUTFChars(model_name, name);
        return JNI_FALSE;
    } catch (...) {
        handle->last_error = "Qwen3-TTS model load failed";
        env->ReleaseStringUTFChars(model_directory, directory);
        env->ReleaseStringUTFChars(model_name, name);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(model_directory, directory);
    env->ReleaseStringUTFChars(model_name, name);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_hiosdra_hreader_adapter_tts_QwenTtsNative_nativeSynthesize(
    JNIEnv* env,
    jobject,
    jlong handle_ptr,
    jstring text,
    jint language_id,
    jint max_audio_tokens,
    jint num_threads,
    jstring speaker,
    jstring instruction
) {
    auto* handle = reinterpret_cast<QwenHandle*>(handle_ptr);
    if (handle == nullptr || handle->context == nullptr || text == nullptr) {
        return nullptr;
    }
    handle->last_error.clear();

    const char* native_text = env->GetStringUTFChars(text, nullptr);
    const char* native_speaker = speaker == nullptr ? nullptr : env->GetStringUTFChars(speaker, nullptr);
    const char* native_instruction = instruction == nullptr ? nullptr : env->GetStringUTFChars(instruction, nullptr);
    if (native_text == nullptr || (speaker != nullptr && native_speaker == nullptr) ||
        (instruction != nullptr && native_instruction == nullptr)) {
        if (native_text != nullptr) env->ReleaseStringUTFChars(text, native_text);
        if (native_speaker != nullptr) env->ReleaseStringUTFChars(speaker, native_speaker);
        if (native_instruction != nullptr) env->ReleaseStringUTFChars(instruction, native_instruction);
        handle->last_error = "Could not read Qwen3-TTS synthesis parameters";
        return nullptr;
    }

    qwen3_tts_result_t result = {};
    try {
        qwen3_tts_params_t params = {};
        params.max_audio_tokens = std::max(1, static_cast<int>(max_audio_tokens));
        params.temperature = 0.9f;
        params.top_p = 1.0f;
        params.top_k = 50;
        params.n_threads = std::clamp(static_cast<int>(num_threads), 1, 4);
        params.print_progress = 0;
        params.print_timing = 0;
        params.repetition_penalty = 1.05f;
        params.language_id = language_id;
        params.speaker = native_speaker;
        params.instruction = native_instruction;
        result = qwen3_tts_synthesize(handle->context, native_text, params);
    } catch (const std::exception& error) {
        handle->last_error = error.what();
    } catch (...) {
        handle->last_error = "Qwen3-TTS synthesis failed";
    }

    env->ReleaseStringUTFChars(text, native_text);
    if (native_speaker != nullptr) env->ReleaseStringUTFChars(speaker, native_speaker);
    if (native_instruction != nullptr) env->ReleaseStringUTFChars(instruction, native_instruction);

    if (result.success == 0 || result.audio == nullptr || result.audio_len <= 0) {
        if (result.error_msg != nullptr) handle->last_error = resultError(result);
        qwen3_tts_free_result(result);
        return nullptr;
    }

    jfloatArray audio = env->NewFloatArray(result.audio_len);
    if (audio == nullptr) {
        handle->last_error = "Could not allocate Qwen3-TTS audio buffer";
        qwen3_tts_free_result(result);
        return nullptr;
    }
    env->SetFloatArrayRegion(audio, 0, result.audio_len, result.audio);
    qwen3_tts_free_result(result);
    handle->last_error.clear();
    return audio;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hiosdra_hreader_adapter_tts_QwenTtsNative_nativeLastError(
    JNIEnv* env,
    jobject,
    jlong handle_ptr
) {
    auto* handle = reinterpret_cast<QwenHandle*>(handle_ptr);
    if (handle == nullptr || handle->last_error.empty()) {
        return env->NewStringUTF("Qwen3-TTS operation failed");
    }
    return env->NewStringUTF(handle->last_error.c_str());
}
