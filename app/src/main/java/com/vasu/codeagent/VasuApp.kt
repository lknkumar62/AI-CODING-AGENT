package com.vasu.codeagent

import android.app.Application
import com.vasu.codeagent.data.repository.ChatHistoryStore
import com.vasu.codeagent.data.repository.ChatRepository
import com.vasu.codeagent.data.repository.GitHubRepository
import com.vasu.codeagent.data.settings.SecureSettingsStore

class VasuApp : Application() {
    lateinit var settingsStore: SecureSettingsStore
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var gitHubRepository: GitHubRepository
        private set
    lateinit var chatHistoryStore: ChatHistoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SecureSettingsStore(this)
        chatRepository = ChatRepository()
        gitHubRepository = GitHubRepository()
        chatHistoryStore = ChatHistoryStore(this)
    }
}
