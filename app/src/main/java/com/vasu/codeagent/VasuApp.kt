package com.vasu.codeagent

import android.app.Application
import com.vasu.codeagent.data.repository.ChatRepository
import com.vasu.codeagent.data.settings.SecureSettingsStore

/**
 * Holds simple app-wide singletons for Phase 1. Replace with a DI
 * framework (Hilt) once the agent/tool/git layers land in later phases.
 */
class VasuApp : Application() {
    lateinit var settingsStore: SecureSettingsStore
        private set
    lateinit var chatRepository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SecureSettingsStore(this)
        chatRepository = ChatRepository()
    }
}
