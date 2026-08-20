package com.vasu.codeagent.ai.provider

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double,
)

@Serializable
data class ChatChoice(
    val message: ChatMessageDto,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
data class AIProviderConfig(
    val label: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.2,
) {
    fun isUsable(): Boolean = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        fun openRouterFreeCoderPreset() = AIProviderConfig(
            label = "OpenRouter Free Coder",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "qwen/qwen3-coder:free",
            temperature = 0.2,
        )

        fun localOllamaPreset() = AIProviderConfig(
            label = "Local Ollama",
            baseUrl = "http://10.0.2.2:11434/v1",
            model = "qwen3-coder:30b",
            temperature = 0.2,
        )
    }
}
