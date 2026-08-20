package com.vasu.codeagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.ai.provider.AIProviderConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: VasuApp,
) : ViewModel() {
    val providerConfig: StateFlow<AIProviderConfig> = app.settingsStore.providerConfig
    val autoApproveSafeOps = app.settingsStore.autoApproveSafeOps

    fun save(config: AIProviderConfig) {
        viewModelScope.launch { app.settingsStore.saveProviderConfig(config) }
    }

    fun setAutoApprove(enabled: Boolean) {
        app.settingsStore.setAutoApproveSafeOps(enabled)
    }

    fun clearAll() {
        app.settingsStore.clearAll()
    }

    fun applyPreset(config: AIProviderConfig) = save(config)

    companion object {
        fun factory(app: VasuApp) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T
        }
    }
}
