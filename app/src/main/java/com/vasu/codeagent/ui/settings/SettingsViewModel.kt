package com.vasu.codeagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.ai.provider.AIProviderConfig
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val app: VasuApp) : ViewModel() {
    val providerConfig: StateFlow<AIProviderConfig> = app.settingsStore.providerConfig
    val autoApproveSafeOps: StateFlow<Boolean> = app.settingsStore.autoApproveSafeOps
    val githubToken: StateFlow<String> = app.settingsStore.githubToken
    fun save(config: AIProviderConfig) = app.settingsStore.saveProviderConfig(config)
    fun setAutoApprove(enabled: Boolean) = app.settingsStore.setAutoApproveSafeOps(enabled)
    fun applyPreset(preset: AIProviderConfig) = app.settingsStore.saveProviderConfig(preset)
    fun saveGithubToken(token: String) = app.settingsStore.saveGithubToken(token)
    fun clearAll() = app.settingsStore.clearAll()
    companion object { fun factory(app: VasuApp) = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T } }
}
