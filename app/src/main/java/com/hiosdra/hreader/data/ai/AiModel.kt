package com.hiosdra.hreader.data.ai

enum class AiModel(
    val displayName: String,
    val modelId: String,
    val description: String
) {
    GPT_OSS_20B("GPT OSS 20B", "openai/gpt-oss-20b:free", "OpenAI's open-source large model"),
    GLM_4_5_AIR("GLM 4.5 Air", "z-ai/glm-4.5-air:free", "Zhipu AI's efficient conversational model"),
    DEEPSEEK_R1("DeepSeek R1-0528", "deepseek/deepseek-r1-0528:free", "DeepSeek's reasoning-focused model"),
    DEEPSEEK_CHAT_V3("DeepSeek Chat V3-0324", "deepseek/deepseek-chat-v3-0324:free", "DeepSeek's conversational model"),
    DEEPSEEK_QWEN3_8B("DeepSeek Qwen3 8B", "deepseek/deepseek-r1-0528-qwen3-8b:free", "DeepSeek's reasoning-focused model"),
    KIMI_K2("Kimi K2", "moonshotai/kimi-k2:free", "Moonshot AI's knowledge model"),
    MISTRAL_SMALL_3_2("Mistral Small 3.2 24b", "mistralai/mistral-small-3.2-24b-instruct:free", "Mistral's efficient instruction model"),
    HUNYUAN_A13B("Hunyuan A13B", "tencent/hunyuan-a13b-instruct:free", "Tencent's instruction-tuned model"),
    GEMMA_3N_E4B("Gemma 3N E4B", "google/gemma-3n-e4b-it:free", "Google's advanced Gemma model"),
    GEMMA_3N_E2B("Gemma 3N E2B", "google/gemma-3n-e2b-it:free", "Google's advanced Gemma model"),
    GEMMA_3_12B("Gemma 3 12B", "google/gemma-3-12b-it:free", "Google's advanced Gemma model"),
    QWEN_3_235B("Qwen 3 235B-a22b", "qwen/qwen3-235b-a22b:free", "Qwen's large-scale model"),
    QWEN_3_4B("Qwen 3 4B", "qwen/qwen3-4b:free", "Qwen's compact efficient model"),
    QWEN_3_30B("Qwen 3 30B-a3b", "qwen/qwen3-30b-a3b:free", "Qwen's balanced performance model"),
    QWEN_3_8B("Qwen 3 8B", "qwen/qwen3-8b:free", "Qwen's large-scale model"),
    QWEN_3_14B("Qwen 3 14B", "qwen/qwen3-14b:free", "Qwen's large-scale model");

    companion object {
        fun getDefault(): AiModel = GPT_OSS_20B
        fun get3MostImportant(): List<AiModel> = listOf(GPT_OSS_20B, GLM_4_5_AIR, QWEN_3_4B)
    }
}
