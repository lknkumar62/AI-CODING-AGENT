package com.vasu.codeagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.data.repository.StoredChatMessage
import com.vasu.codeagent.data.repository.isOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val app: VasuApp,
) : ViewModel() {
    data class UiMessage(val role: String, val content: String)

    private val _messages = MutableStateFlow<List<UiMessage>>(
        app.chatHistoryStore.load().map { UiMessage(it.role, it.content) },
    )
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun send(text: String) {
        if (_isSending.value) return
        updateMessages(_messages.value + UiMessage("user", text))
        _isSending.value = true

        viewModelScope.launch {
            try {
                val history = _messages.value.map { ChatMessageDto(it.role, it.content) }
                val result = app.chatRepository.send(
                    config = app.settingsStore.providerConfig.value,
                    history = history,
                    isOnline = isOnline(app),
                )
                val reply = when (result) {
                    is AIClientResult.Success -> result.text
                    is AIClientResult.ApiError -> "API error ${result.httpCode}: ${result.message}"
                    is AIClientResult.NetworkError -> "Network error: ${result.message}"
                    is AIClientResult.Offline -> result.reason
                }
                updateMessages(_messages.value + UiMessage("assistant", reply))
            } catch (e: Exception) {
                updateMessages(_messages.value + UiMessage("assistant", "Error: ${e.message ?: "Unknown error"}"))
            } finally {
                _isSending.value = false
            }
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
        fun factory(app: VasuApp) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app) as T
        }
    }
}
