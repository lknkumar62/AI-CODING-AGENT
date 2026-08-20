package com.vasu.codeagent.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vasu.codeagent.ai.provider.AIProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists provider settings (including the API key) using an
 * Android-Keystore-backed EncryptedSharedPreferences file. The API key
 * never touches plain-text storage, logs, or the on-screen chat/prompt.
 *
 * GitHub tokens follow the same pattern once GitHub auth (Phase 4) lands —
 * a separate encrypted entry, never embedded in AI prompts.
 */
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

    fun saveProviderConfig(config: AIProviderConfig) {
        prefs.edit().putString(KEY_PROVIDER_CONFIG, json.encodeToString(config)).apply()
        _providerConfig.value = config
    }

    fun setAutoApproveSafeOps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_APPROVE, enabled).apply()
        _autoApproveSafeOps.value = enabled
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _providerConfig.value = AIProviderConfig()
        _autoApproveSafeOps.value = false
    }

    private fun loadProviderConfig(): AIProviderConfig {
        val raw = prefs.getString(KEY_PROVIDER_CONFIG, null) ?: return AIProviderConfig()
        return runCatching { json.decodeFromString(AIProviderConfig.serializer(), raw) }
            .getOrDefault(AIProviderConfig())
    }

    companion object {
        private const val KEY_PROVIDER_CONFIG = "provider_config"
        private const val KEY_AUTO_APPROVE = "auto_approve_safe_ops"
    }
}
