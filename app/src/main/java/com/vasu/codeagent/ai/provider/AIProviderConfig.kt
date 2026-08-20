package com.vasu.codeagent.ai.provider

import kotlinx.serialization.Serializable

/**
 * A user-configured connection to an OpenAI-compatible endpoint.
 * This is the single abstraction every provider (OpenRouter, a custom
 * OpenAI-compatible API, a local Ollama instance, or any future
 * provider) is expressed through — no provider-specific code paths.
 */
@Serializable
data class AIProviderConfig(
    val label: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.2,
    val maxTokens: Int? = null,
) {
    /** OpenRouter and most cloud providers require a key; local Ollama typically does not. */
    fun isUsable(): Boolean = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        /** Current OpenRouter free router. It avoids relying on deprecated free model slugs. */
        fun openRouterFreeCoderPreset() = AIProviderConfig(
            label = "OpenRouter — free coder",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "",
            model = "openrouter/free",
            temperature = 0.2,
        )

        fun localOllamaPreset() = AIProviderConfig(
            label = "Local Ollama",
            baseUrl = "http://127.0.0.1:11434/v1",
            apiKey = "ollama",
            model = "qwen2.5-coder",
            temperature = 0.2,
        )
    }
}
