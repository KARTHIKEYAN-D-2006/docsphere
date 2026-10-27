package com.docsphere.ai.offline

/**
 * Represents on-device LLM models for LiteRT-LM, offering exactly the same 7 models
 * as Google AI Edge Gallery's AI Chat, with identical URLs, sizes, and file names.
 *
 * Each model is downloaded in the .litertlm format and stored in the app's files directory.
 */
enum class LlmModel(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val sizeGb: Float,
    val sizeLabel: String,
    val sizeInBytes: Long,
    val parameters: String,
    val fileName: String,
    val huggingFaceUrl: String,
    val minRamGb: Int,
    val isRecommended: Boolean,
    val iconRes: Int,
    val logoRes: Int? = null,
    val isBuiltIn: Boolean = false,
    val isSupported: Boolean = true,
    val isGated: Boolean = false,
    val isReasoning: Boolean = false
) {
    BUILT_IN(
        id = "built_in",
        displayName = "Docsphere Instant Engine",
        subtitle = "Built-in • Always Available",
        description = "Zero-lag on-device heuristic reasoning. Works instantly with no download. Best for structured document extraction, PAN/Aadhaar lookup, and invoice parsing.",
        sizeGb = 0f,
        sizeLabel = "Built-in",
        sizeInBytes = 0L,
        parameters = "Heuristic",
        fileName = "",
        huggingFaceUrl = "",
        minRamGb = 0,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.docsphere_ai_logo,
        logoRes = com.docsphere.R.drawable.docsphere_ai_logo,
        isBuiltIn = true,
        isSupported = true
    ),

    GEMMA_4_E2B(
        id = "gemma_4_e2b",
        displayName = "Gemma-4-E2B-it",
        subtitle = "Default for document questions (LiteRT-LM • 2.6 GB)",
        description = "Default model for document questions when downloaded. Delivers excellent document comprehension on phones with 8 GB+ RAM.",
        sizeGb = 2.6f,
        sizeLabel = "~2.6 GB",
        sizeInBytes = 2_588_147_712L,
        parameters = "2B",
        fileName = "gemma-4-E2B-it.litertlm",
        huggingFaceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        minRamGb = 8,
        isRecommended = true,
        iconRes = com.docsphere.R.drawable.ic_model_gemma,
        logoRes = com.docsphere.R.drawable.logo_model_gemma_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = false
    ),

    GEMMA_4_E4B(
        id = "gemma_4_e4b",
        displayName = "Gemma-4-E4B-it",
        subtitle = "LiteRT-LM • High Quality (3.7 GB)",
        description = "Google's highest fidelity on-device model for complex reasoning and synthesis. Requires a device with 12 GB RAM.",
        sizeGb = 3.7f,
        sizeLabel = "~3.7 GB",
        sizeInBytes = 3_659_530_240L,
        parameters = "4B",
        fileName = "gemma-4-E4B-it.litertlm",
        huggingFaceUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        minRamGb = 12,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_gemma,
        logoRes = com.docsphere.R.drawable.logo_model_gemma_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = false
    ),

    GEMMA_3N_E2B(
        id = "gemma_3n_e2b",
        displayName = "Gemma-3n-E2B-it",
        subtitle = "LiteRT-LM • Multimodal (3.7 GB, Gated)",
        description = "A variant of Gemma 3n E2B ready for deployment on Android using LiteRT-LM. Gated model requiring Hugging Face token.",
        sizeGb = 3.7f,
        sizeLabel = "~3.7 GB",
        sizeInBytes = 3_655_827_456L,
        parameters = "2B",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        huggingFaceUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        minRamGb = 8,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_gemma,
        logoRes = com.docsphere.R.drawable.logo_model_gemma_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = true
    ),

    GEMMA_3N_E4B(
        id = "gemma_3n_e4b",
        displayName = "Gemma-3n-E4B-it",
        subtitle = "LiteRT-LM • Multimodal High-Fi (4.9 GB, Gated)",
        description = "A variant of Gemma 3n E4B ready for deployment on Android using LiteRT-LM. Gated model requiring Hugging Face token.",
        sizeGb = 4.9f,
        sizeLabel = "~4.9 GB",
        sizeInBytes = 4_919_541_760L,
        parameters = "4B",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        huggingFaceUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm",
        minRamGb = 12,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_gemma,
        logoRes = com.docsphere.R.drawable.logo_model_gemma_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = true
    ),

    GEMMA3_1B(
        id = "gemma3_1b",
        displayName = "Gemma3-1B-IT",
        subtitle = "General chat & conversation (LiteRT-LM • 584 MB, Gated)",
        description = "Fast model for general chat and conversation with low RAM overhead and rapid responsiveness. Gated model requiring Hugging Face token.",
        sizeGb = 0.584f,
        sizeLabel = "~584 MB",
        sizeInBytes = 584_417_280L,
        parameters = "1B",
        fileName = "gemma3-1b-it-int4.litertlm",
        huggingFaceUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        minRamGb = 6,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_gemma,
        logoRes = com.docsphere.R.drawable.logo_model_gemma_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = true
    ),

    QWEN2_5_1_5B(
        id = "qwen2_5_1_5b",
        displayName = "Qwen2.5-1.5B-Instruct",
        subtitle = "LiteRT-LM • Multilingual (1.6 GB)",
        description = "High-performing alternative on-device model with 8-bit quantization for multilingual and structured tasks.",
        sizeGb = 1.6f,
        sizeLabel = "~1.6 GB",
        sizeInBytes = 1_597_922_856L,
        parameters = "1.5B",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        huggingFaceUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        minRamGb = 6,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_qwen3,
        logoRes = com.docsphere.R.drawable.logo_model_qwen_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = false
    ),

    DEEPSEEK_R1_1_5B(
        id = "deepseek_r1_1_5b",
        displayName = "DeepSeek-R1-Distill-Qwen-1.5B",
        subtitle = "Reasoning, slower",
        description = "Distilled reasoning model that thinks step-by-step before answering. Reasoning block is hidden by default.",
        sizeGb = 1.8f,
        sizeLabel = "~1.8 GB",
        sizeInBytes = 1_833_451_520L,
        parameters = "1.5B",
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        huggingFaceUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        minRamGb = 6,
        isRecommended = false,
        iconRes = com.docsphere.R.drawable.ic_model_deepseek,
        logoRes = com.docsphere.R.drawable.logo_model_deepseek_text,
        isBuiltIn = false,
        isSupported = true,
        isGated = false,
        isReasoning = true
    );

    val modelWebUrl: String
        get() = if (huggingFaceUrl.isBlank()) ""
        else huggingFaceUrl.substringBefore("/resolve/main")

    companion object {
        val REMOVED_MODEL_IDS = setOf(
            "gemma_2b",
            "phi4_mini",
            "qwen3_8b",
            "gemma4_12b",
            "mistral_24b"
        )

        fun fromId(id: String?): LlmModel = if (id == null) BUILT_IN else entries.find { it.id == id } ?: BUILT_IN
    }
}
