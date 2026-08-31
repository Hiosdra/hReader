#include <jni.h>

#include "llm/llm.hpp"

#include <algorithm>
#include <exception>
#include <memory>
#include <string>
#include <vector>

namespace {

using MNN::Transformer::Llm;

struct LlmDeleter {
    void operator()(Llm* llm) const {
        if (llm != nullptr) Llm::destroy(llm);
    }
};

struct MnnHandle {
    std::unique_ptr<Llm, LlmDeleter> llm;
    std::string last_error;
};

std::string joinPath(const std::string& directory, const char* name) {
    if (directory.empty() || directory.back() == '/') return directory + name;
    return directory + "/" + name;
}

std::string quoteJson(const std::string& value) {
    std::string result = "\"";
    for (const unsigned char character : value) {
        switch (character) {
            case '\\': result += "\\\\"; break;
            case '\"': result += "\\\""; break;
            case '\b': result += "\\b"; break;
            case '\f': result += "\\f"; break;
            case '\n': result += "\\n"; break;
            case '\r': result += "\\r"; break;
            case '\t': result += "\\t"; break;
            default: result += static_cast<char>(character); break;
        }
    }
    result += "\"";
    return result;
}

bool isSupportedBackend(const std::string& backend) {
    return backend == "cpu" || backend == "opencl" || backend == "vulkan";
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hiosdra_hreader_adapter_tts_MnnTtsNative_nativeCreate(JNIEnv*, jobject) {
    try {
        return reinterpret_cast<jlong>(new MnnHandle());
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hiosdra_hreader_adapter_tts_MnnTtsNative_nativeFree(JNIEnv*, jobject, jlong handle_ptr) {
    delete reinterpret_cast<MnnHandle*>(handle_ptr);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hiosdra_hreader_adapter_tts_MnnTtsNative_nativeLoad(
    JNIEnv* env,
    jobject,
    jlong handle_ptr,
    jstring model_directory,
    jstring config_name,
    jint num_threads,
    jstring backend,
    jstring cache_directory
) {
    auto* handle = reinterpret_cast<MnnHandle*>(handle_ptr);
    if (handle == nullptr || model_directory == nullptr || config_name == nullptr || backend == nullptr ||
        cache_directory == nullptr) {
        return JNI_FALSE;
    }

    const char* directory = env->GetStringUTFChars(model_directory, nullptr);
    const char* config = env->GetStringUTFChars(config_name, nullptr);
    const char* backend_name = env->GetStringUTFChars(backend, nullptr);
    const char* cache = env->GetStringUTFChars(cache_directory, nullptr);
    const auto releaseStrings = [&] {
        if (directory != nullptr) env->ReleaseStringUTFChars(model_directory, directory);
        if (config != nullptr) env->ReleaseStringUTFChars(config_name, config);
        if (backend_name != nullptr) env->ReleaseStringUTFChars(backend, backend_name);
        if (cache != nullptr) env->ReleaseStringUTFChars(cache_directory, cache);
    };
    if (directory == nullptr || config == nullptr || backend_name == nullptr || cache == nullptr) {
        releaseStrings();
        handle->last_error = "Could not read MNN model parameters";
        return JNI_FALSE;
    }

    try {
        handle->last_error.clear();
        const int threads = std::clamp(static_cast<int>(num_threads), 1, 4);
        const std::string directory_string(directory);
        const std::string backend_string(backend_name);
        const std::string cache_string(cache);
        if (!isSupportedBackend(backend_string)) {
            handle->last_error = "Unsupported MNN backend: " + backend_string;
            releaseStrings();
            return JNI_FALSE;
        }
        handle->llm.reset(Llm::createLLM(joinPath(directory_string, config)));
        if (!handle->llm) {
            handle->last_error = "Could not create MNN Qwen3-TTS model";
            releaseStrings();
            return JNI_FALSE;
        }
        const std::string runtime_config =
            "{\"backend_type\":" + quoteJson(backend_string) + ",\"thread_num\":" +
            std::to_string(threads) + ",\"precision\":\"low\",\"memory\":\"low\",\"async\":false,\"tmp_path\":" +
            quoteJson(cache_string) + ",\"mllm\":{\"backend_type\":" + quoteJson(backend_string) +
            ",\"thread_num\":" + std::to_string(threads) + ",\"precision\":\"low\",\"memory\":\"low\"}}";
        if (!handle->llm->set_config(runtime_config) || !handle->llm->load()) {
            handle->last_error = "MNN Qwen3-TTS model load failed";
            releaseStrings();
            return JNI_FALSE;
        }
    } catch (const std::exception& error) {
        handle->last_error = error.what();
        releaseStrings();
        return JNI_FALSE;
    } catch (...) {
        handle->last_error = "MNN Qwen3-TTS model load failed";
        releaseStrings();
        return JNI_FALSE;
    }

    releaseStrings();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_hiosdra_hreader_adapter_tts_MnnTtsNative_nativeSynthesize(
    JNIEnv* env,
    jobject,
    jlong handle_ptr,
    jstring text,
    jstring language,
    jstring reference_audio,
    jint max_frames
) {
    auto* handle = reinterpret_cast<MnnHandle*>(handle_ptr);
    if (handle == nullptr || !handle->llm || text == nullptr || language == nullptr || reference_audio == nullptr) {
        return nullptr;
    }
    handle->last_error.clear();

    const char* native_text = env->GetStringUTFChars(text, nullptr);
    const char* native_language = env->GetStringUTFChars(language, nullptr);
    const char* native_reference_audio = env->GetStringUTFChars(reference_audio, nullptr);
    if (native_text == nullptr || native_language == nullptr || native_reference_audio == nullptr) {
        if (native_text != nullptr) env->ReleaseStringUTFChars(text, native_text);
        if (native_language != nullptr) env->ReleaseStringUTFChars(language, native_language);
        if (native_reference_audio != nullptr) env->ReleaseStringUTFChars(reference_audio, native_reference_audio);
        handle->last_error = "Could not read MNN Qwen3-TTS synthesis parameters";
        return nullptr;
    }

    std::vector<float> waveform;
    bool generated = false;
    try {
        handle->llm->setWavformCallback([&waveform](const float* samples, size_t size, bool) {
            if (samples != nullptr && size > 0) waveform.insert(waveform.end(), samples, samples + size);
            return true;
        });
        generated = handle->llm->generateTTS(
            native_text,
            native_language,
            std::clamp(static_cast<int>(max_frames), 1, 2'048),
            native_reference_audio
        );
    } catch (const std::exception& error) {
        handle->last_error = error.what();
    } catch (...) {
        handle->last_error = "MNN Qwen3-TTS synthesis failed";
    }
    try {
        handle->llm->setWavformCallback({});
    } catch (const std::exception& error) {
        generated = false;
        if (handle->last_error.empty()) handle->last_error = error.what();
    } catch (...) {
        generated = false;
        if (handle->last_error.empty()) handle->last_error = "MNN Qwen3-TTS callback cleanup failed";
    }

    env->ReleaseStringUTFChars(text, native_text);
    env->ReleaseStringUTFChars(language, native_language);
    env->ReleaseStringUTFChars(reference_audio, native_reference_audio);

    if (!generated || waveform.empty()) {
        if (handle->last_error.empty()) handle->last_error = "MNN Qwen3-TTS synthesis failed";
        return nullptr;
    }

    jfloatArray audio = env->NewFloatArray(static_cast<jsize>(waveform.size()));
    if (audio == nullptr) {
        handle->last_error = "Could not allocate MNN Qwen3-TTS audio buffer";
        return nullptr;
    }
    env->SetFloatArrayRegion(audio, 0, static_cast<jsize>(waveform.size()), waveform.data());
    handle->last_error.clear();
    return audio;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hiosdra_hreader_adapter_tts_MnnTtsNative_nativeLastError(
    JNIEnv* env,
    jobject,
    jlong handle_ptr
) {
    auto* handle = reinterpret_cast<MnnHandle*>(handle_ptr);
    if (handle == nullptr || handle->last_error.empty()) {
        return env->NewStringUTF("MNN Qwen3-TTS operation failed");
    }
    return env->NewStringUTF(handle->last_error.c_str());
}
