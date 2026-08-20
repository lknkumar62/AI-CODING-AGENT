package com.vasu.codeagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vasu.codeagent.ai.provider.AIProviderConfig

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val saved by viewModel.providerConfig.collectAsState()
    val autoApprove by viewModel.autoApproveSafeOps.collectAsState()
    val savedGithubToken by viewModel.githubToken.collectAsState()
    var label by remember(saved) { mutableStateOf(saved.label) }
    var baseUrl by remember(saved) { mutableStateOf(saved.baseUrl) }
    var apiKey by remember(saved) { mutableStateOf(saved.apiKey) }
    var model by remember(saved) { mutableStateOf(saved.model) }
    var temperature by remember(saved) { mutableStateOf(saved.temperature.toFloat()) }
    var githubToken by remember(savedGithubToken) { mutableStateOf(savedGithubToken) }
    Column(modifier=Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("AI Provider",style=MaterialTheme.typography.titleLarge)
        Text("Any OpenAI-compatible endpoint works: OpenRouter, a custom API, or a local Ollama server.",style=MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={viewModel.applyPreset(AIProviderConfig.openRouterFreeCoderPreset())}){Text("Use free OpenRouter coder")};OutlinedButton(onClick={viewModel.applyPreset(AIProviderConfig.localOllamaPreset())}){Text("Use local Ollama")}}
        OutlinedTextField(value=label,onValueChange={label=it},label={Text("Label")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=baseUrl,onValueChange={baseUrl=it},label={Text("Base URL")},placeholder={Text("https://openrouter.ai/api/v1")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=apiKey,onValueChange={apiKey=it},label={Text("API Key")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=model,onValueChange={model=it},label={Text("Model")},placeholder={Text("qwen/qwen3-coder:free")},modifier=Modifier.fillMaxWidth())
        Text("Temperature: ${"%.2f".format(temperature)}",style=MaterialTheme.typography.bodyMedium)
        Slider(value=temperature,onValueChange={temperature=it},valueRange=0f..1f)
        Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Auto-approve safe operations",style=MaterialTheme.typography.bodyMedium);Text("Reads/searches only. Edits, deletes, and pushes always ask first.",style=MaterialTheme.typography.labelSmall)};Switch(checked=autoApprove,onCheckedChange=viewModel::setAutoApprove)}
        Button(onClick={viewModel.save(AIProviderConfig(label=label,baseUrl=baseUrl.trim(),apiKey=apiKey,model=model.trim(),temperature=temperature.toDouble()))},modifier=Modifier.fillMaxWidth()){Text("Save")}
        Text("GitHub",style=MaterialTheme.typography.titleLarge)
        Text("Create a fine-grained personal access token with Contents read/write on the repos you want VASU to edit. It is stored encrypted and is never sent to the AI provider or logged.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value=githubToken,onValueChange={githubToken=it},label={Text("GitHub token")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={viewModel.saveGithubToken(githubToken.trim())},modifier=Modifier.fillMaxWidth()){Text("Save GitHub token")}
        OutlinedButton(onClick={viewModel.clearAll},modifier=Modifier.fillMaxWidth()){Text("Clear all stored settings")}
    }
}
