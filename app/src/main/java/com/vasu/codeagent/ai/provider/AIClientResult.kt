package com.vasu.codeagent.ai.provider

sealed interface AIClientResult {
    data class Success(val text: String, val usage: ChatUsage?) : AIClientResult
    data class ApiError(val httpCode: Int, val message: String) : AIClientResult
    data class NetworkError(val message: String) : AIClientResult
    /** No provider is configured yet, or the device has no connection. */
    data class Offline(val reason: String) : AIClientResult
}
