package com.hiosdra.hreader.data.ai

enum class AiModel(
    val displayName: String,
    val modelId: String,
    val description: String
) {
    LLAMA_3_2_3B("Llama 3.2 3B", "meta-llama/llama-3.2-3b-instruct:free", "Fast and efficient for summaries"),
    LLAMA_3_2_1B("Llama 3.2 1B", "meta-llama/llama-3.2-1b-instruct:free", "Lightweight model for basic summaries"),
    QWEN_2_5_1_5B("Qwen 2.5 1.5B", "qwen/qwen-2.5-1.5b-instruct:free", "Good balance of speed and quality"),
    GEMMA_2_2B("Gemma 2 2B", "google/gemma-2-2b-it:free", "Google's efficient model for text generation"),
    GPT_OSS_20B("GPT OSS 20B", "openai/gpt-oss-20b:free", "OpenAI's open-source large model"),
    GLM_4_5_AIR("GLM 4.5 Air", "z-ai/glm-4.5-air:free", "Zhipu AI's efficient conversational model"),
    DEEPSEEK_R1("DeepSeek R1", "deepseek/deepseek-r1-0528:free", "DeepSeek's reasoning-focused model"),
    DEEPSEEK_CHAT_V3("DeepSeek Chat V3", "deepseek/deepseek-chat-v3-0324:free", "DeepSeek's conversational model"),
    KIMI_K2("Kimi K2", "moonshotai/kimi-k2:free", "Moonshot AI's knowledge model"),
    QWEN_3_235B("Qwen 3 235B", "qwen/qwen3-235b-a22b:free", "Qwen's large-scale model"),
    MISTRAL_SMALL_3_2("Mistral Small 3.2", "mistralai/mistral-small-3.2-24b-instruct:free", "Mistral's efficient instruction model"),
    HUNYUAN_A13B("Hunyuan A13B", "tencent/hunyuan-a13b-instruct:free", "Tencent's instruction-tuned model"),
    GEMMA_3N_E4B("Gemma 3N E4B", "google/gemma-3n-e4b-it:free", "Google's advanced Gemma model"),
    QWEN_3_4B("Qwen 3 4B", "qwen/qwen3-4b:free", "Qwen's compact efficient model"),
    QWEN_3_30B("Qwen 3 30B", "qwen/qwen3-30b-a3b:free", "Qwen's balanced performance model");
    
    companion object {
        fun getDefault(): AiModel = LLAMA_3_2_3B
    }
}