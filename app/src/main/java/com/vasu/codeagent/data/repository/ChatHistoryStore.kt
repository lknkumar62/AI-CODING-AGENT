package com.vasu.codeagent.data.repository

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredChatMessage(val role: String, val content: String)

/**
 * Persists the Agent tab's conversation locally (plain prefs — this is chat
 * text, not a credential, so it doesn't need Keystore encryption) so the
 * thread survives navigating away, backgrounding, or restarting the app.
 */
class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("vasu_chat_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<StoredChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredChatMessage>>(raw)
        }.getOrDefault(emptyList())
    }

    fun save(messages: List<StoredChatMessage>) {
        prefs.edit()
            .putString(KEY_MESSAGES, json.encodeToString(messages))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
    }
}
