package com.vasu.codeagent.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vasu.codeagent.ai.provider.AIProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SecureSettingsStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "vasu_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _providerConfig = MutableStateFlow(loadProviderConfig())
    val providerConfig: StateFlow<AIProviderConfig> = _providerConfig

    private val _autoApproveSafeOps = MutableStateFlow(prefs.getBoolean(KEY_AUTO_APPROVE, false))
    val autoApproveSafeOps: StateFlow<Boolean> = _autoApproveSafeOps

    private val _githubToken = MutableStateFlow(prefs.getString(KEY_GITHUB_TOKEN, "").orEmpty())
    val githubToken: StateFlow<String> = _githubToken

    private val _lastRepo = MutableStateFlow(prefs.getString(KEY_LAST_REPO, "").orEmpty())
    val lastRepo: StateFlow<String> = _lastRepo

    fun saveProviderConfig(config: AIProviderConfig) {
        prefs.edit().putString(KEY_PROVIDER_CONFIG, json.encodeToString(config)).apply()
        _providerConfig.value = config
    }

    fun setAutoApproveSafeOps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_APPROVE, enabled).apply()
        _autoApproveSafeOps.value = enabled
    }

    fun saveGithubToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
        _githubToken.value = token
    }

    fun saveLastRepo(repo: String) {
        prefs.edit().putString(KEY_LAST_REPO, repo).apply()
        _lastRepo.value = repo
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _providerConfig.value = AIProviderConfig()
        _autoApproveSafeOps.value = false
        _githubToken.value = ""
        _lastRepo.value = ""
    }

    private fun loadProviderConfig(): AIProviderConfig {
        val raw = prefs.getString(KEY_PROVIDER_CONFIG, null) ?: return AIProviderConfig()
        return runCatching { json.decodeFromString(AIProviderConfig.serializer(), raw) }
            .getOrDefault(AIProviderConfig())
    }

    companion object {
        private const val KEY_PROVIDER_CONFIG = "provider_config"
        private const val KEY_AUTO_APPROVE = "auto_approve_safe_ops"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_LAST_REPO = "last_repo"
    }
}
