package com.vasu.codeagent.ai.provider

sealed interface AIClientResult {
    data class Success(val text: String) : AIClientResult
    data class Error(val message: String) : AIClientResult
}
