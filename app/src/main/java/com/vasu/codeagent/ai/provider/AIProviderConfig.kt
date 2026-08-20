package com.vasu.codeagent.ai.provider

import kotlinx.serialization.Serializable

@Serializable
data class AIProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
)
