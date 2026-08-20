package com.vasu.codeagent.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class ChatToolCall(
    val id: String,
    val type: String = "function",
    val function: ChatFunctionCall,
)

@Serializable
data class ChatFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<ChatToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
)

@Serializable
data class ChatToolDefinition(
    val type: String = "function",
    val function: ChatFunctionDefinition,
)

@Serializable
data class ChatFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
