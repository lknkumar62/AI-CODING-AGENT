package com.vasu.codeagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.data.repository.isOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val app: VasuApp,
) : ViewModel() {
    data class UiMessage(val role: String, val content: String)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun send(text: String) {
        if (_isSending.value) return
        _messages.value = _messages.value + UiMessage("user", text)
        _isSending.value = true

        viewModelScope.launch {
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
            _messages.value = _messages.value + UiMessage("assistant", reply)
            _isSending.value = false
        }
    }

    companion object {
        fun factory(app: VasuApp) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app) as T
        }
    }
}
