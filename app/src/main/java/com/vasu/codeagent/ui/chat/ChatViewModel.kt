package com.vasu.codeagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.agent.AgentToolExecutor
import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.data.repository.StoredChatMessage
import com.vasu.codeagent.data.repository.isOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ChatViewModel(
    private val app: VasuApp,
) : ViewModel() {
    data class UiMessage(val role: String, val content: String)

    @Serializable
    private data class ToolCall(val name: String, val arguments: JsonObject = JsonObject(emptyMap()))

    data class PendingApproval(val toolName: String, val arguments: JsonObject)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val toolExecutor = AgentToolExecutor(app.gitHubRepository, app.settingsStore)

    private val _messages = MutableStateFlow<List<UiMessage>>(
        app.chatHistoryStore.load().map { UiMessage(it.role, it.content) },
    )
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
    val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

    private var pendingHistory: List<ChatMessageDto> = emptyList()

    fun send(text: String) {
        if (_isSending.value || _pendingApproval.value != null) return
        updateMessages(_messages.value + UiMessage("user", text))
        _isSending.value = true
        viewModelScope.launch {
            runAgent(_messages.value.map { ChatMessageDto(it.role, it.content) })
        }
    }

    fun approvePending() {
        val pending = _pendingApproval.value ?: return
        _pendingApproval.value = null
        _isSending.value = true
        viewModelScope.launch {
            try {
                val result = toolExecutor.execute(pending.toolName, pending.arguments)
                val history = pendingHistory + ChatMessageDto(
                    "user",
                    "<tool_result name=\"${pending.toolName}\" ok=\"${result.ok}\">${result.text}</tool_result>",
                )
                runAgent(history)
            } catch (e: Exception) {
                updateMessages(_messages.value + UiMessage("assistant", "GitHub operation failed: ${e.message ?: "Unknown error"}"))
                _isSending.value = false
            }
        }
    }

    fun rejectPending() {
        _pendingApproval.value = null
        pendingHistory = emptyList()
        _isSending.value = false
        updateMessages(_messages.value + UiMessage("assistant", "Operation cancelled. No GitHub change was made."))
    }

    private suspend fun runAgent(initialHistory: List<ChatMessageDto>) {
        var history = initialHistory
        pendingHistory = history

        try {
            if (!isOnline(app)) {
                updateMessages(_messages.value + UiMessage("assistant", "Offline — GitHub/AI agent requires an internet connection."))
                _isSending.value = false
                return
            }

            repeat(MAX_AGENT_STEPS) {
                val result = app.chatRepository.send(
                    config = app.settingsStore.providerConfig.value,
                    history = history,
                    isOnline = true,
                )

                when (result) {
                    is AIClientResult.Success -> {
                        val reply = result.text.trim()
                        val call = parseToolCall(reply)
                        if (call == null) {
                            updateMessages(_messages.value + UiMessage("assistant", reply))
                            _isSending.value = false
                            return
                        }

                        history = history + ChatMessageDto("assistant", reply)
                        if (!toolExecutor.isSafe(call.name) && !app.settingsStore.autoApproveSafeOps.value) {
                            pendingHistory = history
                            _pendingApproval.value = PendingApproval(call.name, call.arguments)
                            return
                        }

                        val toolResult = toolExecutor.execute(call.name, call.arguments)
                        history = history + ChatMessageDto(
                            "user",
                            "<tool_result name=\"${call.name}\" ok=\"${toolResult.ok}\">${toolResult.text}</tool_result>",
                        )
                    }
                    is AIClientResult.ApiError -> {
                        updateMessages(_messages.value + UiMessage("assistant", "API error ${result.httpCode}: ${result.message}"))
                        _isSending.value = false
                        return
                    }
                    is AIClientResult.NetworkError -> {
                        updateMessages(_messages.value + UiMessage("assistant", "Network error: ${result.message}"))
                        _isSending.value = false
                        return
                    }
                    is AIClientResult.Offline -> {
                        updateMessages(_messages.value + UiMessage("assistant", result.reason))
                        _isSending.value = false
                        return
                    }
                }
            }

            updateMessages(_messages.value + UiMessage("assistant", "I reached the agent step limit before finishing. Tell me to continue."))
        } catch (e: Exception) {
            updateMessages(_messages.value + UiMessage("assistant", "Agent error: ${e.message ?: "Unknown error"}"))
        } finally {
            if (_pendingApproval.value == null) {
                _isSending.value = false
            }
        }
    }

    private fun parseToolCall(text: String): ToolCall? {
        val start = text.indexOf("<tool_call>")
        if (start < 0) return null
        val end = text.indexOf("</tool_call>", start + 11)
        if (end < 0) return null
        val payload = text.substring(start + 11, end).trim()
        return runCatching { json.decodeFromString<ToolCall>(payload) }.getOrNull()
    }

    fun clearHistory() {
        app.chatHistoryStore.clear()
        _messages.value = emptyList()
    }

    private fun updateMessages(newList: List<UiMessage>) {
        _messages.value = newList
        app.chatHistoryStore.save(newList.map { StoredChatMessage(it.role, it.content) })
    }

    companion object {
        private const val MAX_AGENT_STEPS = 8

        fun factory(app: VasuApp) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app) as T
        }
    }
}
