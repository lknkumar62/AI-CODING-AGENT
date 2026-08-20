package com.vasu.codeagent.ai.provider

sealed interface AIClientResult {
    data class Success(
        val text: String,
        val usage: ChatUsage?,
        val toolCalls: List<ChatToolCall> = emptyList(),
    ) : AIClientResult

    data class ApiError(val httpCode: Int, val message: String) : AIClientResult
    data class NetworkError(val message: String) : AIClientResult
    data class Offline(val reason: String) : AIClientResult
}
