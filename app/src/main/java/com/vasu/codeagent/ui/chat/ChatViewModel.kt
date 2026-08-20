package com.vasu.codeagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.agent.AgentToolExecutor
import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.ai.provider.ChatToolCall
import com.vasu.codeagent.data.repository.StoredChatMessage
import com.vasu.codeagent.data.repository.isOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ChatViewModel(private val app: VasuApp) : ViewModel() {
    data class UiMessage(val role: String, val content: String)
    data class PendingApproval(val toolName: String, val arguments: JsonObject, val callId: String)

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
                    role = "tool",
                    content = result.text,
                    toolCallId = pending.callId,
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
                        val calls = result.toolCalls
                        if (calls.isEmpty()) {
                            val reply = result.text.trim()
                            if (reply.isNotEmpty()) updateMessages(_messages.value + UiMessage("assistant", reply))
                            _isSending.value = false
                            return
                        }

                        // The prompt asks the model to issue one operation at a time. This keeps
                        // approval and tool-result ordering valid for OpenAI-compatible APIs.
                        val call = calls.first()
                        val arguments = runCatching {
                            json.parseToJsonElement(call.function.arguments).jsonObject
                        }.getOrElse {
                            updateMessages(_messages.value + UiMessage("assistant", "Invalid tool arguments from model: ${it.message}"))
                            _isSending.value = false
                            return
                        }

                        history = history + ChatMessageDto(
                            role = "assistant",
                            content = result.text.ifBlank { null },
                            toolCalls = listOf(call),
                        )
                        pendingHistory = history

                        if (!toolExecutor.isSafe(call.function.name) && !app.settingsStore.autoApproveSafeOps.value) {
                            _pendingApproval.value = PendingApproval(call.function.name, arguments, call.id)
                            return
                        }

                        val toolResult = toolExecutor.execute(call.function.name, arguments)
                        history = history + ChatMessageDto(
                            role = "tool",
                            content = toolResult.text,
                            toolCallId = call.id,
                        )
                        pendingHistory = history
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

            updateMessages(_messages.value + UiMessage("assistant", "Agent reached the ${MAX_AGENT_STEPS}-step safety limit. Send \"continue\" to resume."))
        } catch (e: Exception) {
            updateMessages(_messages.value + UiMessage("assistant", "Agent error: ${e.message ?: "Unknown error"}"))
        } finally {
            if (_pendingApproval.value == null) _isSending.value = false
        }
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
        private const val MAX_AGENT_STEPS = 16

        fun factory(app: VasuApp) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app) as T
        }
    }
}
