package com.docsphere.ai.offline

/**
 * Configuration options for an on-device LiteRT-LM model.
 * Matches Google AI Edge Gallery's model configuration and sampler settings.
 */
data class ModelConfig(
    val accelerator: String = "GPU", // "GPU" or "CPU"
    val maxTokens: Int = 4096,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val gpuFailed: Boolean = false
) {
    companion object {
        fun defaultFor(model: LlmModel): ModelConfig {
            return when (model) {
                LlmModel.GEMMA_4_E2B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 0.8f,
                    topK = 40,
                    topP = 0.95f
                )
                LlmModel.GEMMA_4_E4B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 0.8f,
                    topK = 40,
                    topP = 0.95f
                )
                LlmModel.GEMMA_3N_E2B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 1.0f,
                    topK = 64,
                    topP = 0.95f
                )
                LlmModel.GEMMA_3N_E4B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 1.0f,
                    topK = 64,
                    topP = 0.95f
                )
                LlmModel.GEMMA3_1B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 2048,
                    temperature = 0.8f,
                    topK = 40,
                    topP = 0.95f
                )
                LlmModel.QWEN2_5_1_5B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 0.7f,
                    topK = 40,
                    topP = 0.95f
                )
                LlmModel.DEEPSEEK_R1_1_5B -> ModelConfig(
                    accelerator = "GPU",
                    maxTokens = 4096,
                    temperature = 0.6f,
                    topK = 40,
                    topP = 0.95f
                )
                LlmModel.BUILT_IN -> ModelConfig(
                    accelerator = "CPU",
                    maxTokens = 2048,
                    temperature = 0.8f,
                    topK = 40,
                    topP = 0.95f
                )
            }
        }
    }
}
