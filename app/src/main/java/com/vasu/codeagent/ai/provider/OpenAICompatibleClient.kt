package com.vasu.codeagent.ai.provider

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OpenAI-compatible client with structured tool-calling support. */
class OpenAICompatibleClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val logging = HttpLoggingInterceptor { message ->
        val redacted = message.replace(Regex("Bearer [A-Za-z0-9._-]+"), "Bearer [REDACTED]")
        android.util.Log.d("VasuAI", redacted)
    }.apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(OpenAICompatibleApi::class.java)

    suspend fun sendChat(
        config: AIProviderConfig,
        systemPrompt: String,
        history: List<ChatMessageDto>,
        isNetworkAvailable: Boolean,
        tools: List<ChatToolDefinition> = emptyList(),
    ): AIClientResult {
        if (!config.isUsable()) return AIClientResult.Offline("No AI provider configured. Add one in Settings.")
        if (!isNetworkAvailable) return AIClientResult.Offline("Offline — AI provider unavailable")

        val url = buildChatCompletionsUrl(config.baseUrl)
        val headers = buildMap {
            put("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
        }
        val request = ChatCompletionRequest(
            model = config.model,
            messages = listOf(ChatMessageDto("system", systemPrompt)) + history,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            tools = tools.takeIf { it.isNotEmpty() },
            toolChoice = "auto".takeIf { tools.isNotEmpty() },
        )

        return try {
            val response = api.chatCompletions(url, headers, request)
            if (response.isSuccessful) {
                val message = response.body()?.choices?.firstOrNull()?.message
                if (message == null) {
                    AIClientResult.ApiError(response.code(), "Provider returned an empty response.")
                } else {
                    AIClientResult.Success(
                        text = message.content.orEmpty(),
                        usage = response.body()?.usage,
                        toolCalls = message.toolCalls.orEmpty(),
                    )
                }
            } else {
                val errText = response.errorBody()?.string()
                val parsedMessage = errText?.let { raw ->
                    runCatching { json.decodeFromString<ApiErrorBody>(raw).error?.message }.getOrNull()
                }
                AIClientResult.ApiError(
                    response.code(),
                    parsedMessage ?: "Request failed (HTTP ${response.code()}).",
                )
            }
        } catch (e: IOException) {
            AIClientResult.NetworkError(e.message ?: "Network request failed.")
        } catch (e: Exception) {
            AIClientResult.NetworkError(e.message ?: "Unexpected client error.")
        }
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}
