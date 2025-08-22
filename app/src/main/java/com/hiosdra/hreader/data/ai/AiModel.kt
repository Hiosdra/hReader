package com.hiosdra.hreader.data.ai

enum class AiModel(
    val displayName: String,
    val modelId: String,
    val description: String
) {
    LLAMA_3_2_3B("Llama 3.2 3B", "meta-llama/llama-3.2-3b-instruct:free", "Fast and efficient for summaries"),
    LLAMA_3_2_1B("Llama 3.2 1B", "meta-llama/llama-3.2-1b-instruct:free", "Lightweight model for basic summaries"),
    QWEN_2_5_1_5B("Qwen 2.5 1.5B", "qwen/qwen-2.5-1.5b-instruct:free", "Good balance of speed and quality"),
    GEMMA_2_2B("Gemma 2 2B", "google/gemma-2-2b-it:free", "Google's efficient model for text generation");
    
    companion object {
        fun getDefault(): AiModel = LLAMA_3_2_3B
    }
}