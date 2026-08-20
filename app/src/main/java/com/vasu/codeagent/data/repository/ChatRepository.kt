package com.vasu.codeagent.data.repository

import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.AIProviderConfig
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.ai.provider.OpenAICompatibleClient

class ChatRepository(private val client: OpenAICompatibleClient = OpenAICompatibleClient()) {
    companion object {
        val SYSTEM_PROMPT = """
You are VASU CODE AGENT, a professional software engineering agent running on a mobile device.
Never modify files blindly. Inspect relevant repository structure first, identify the minimum
files required for a task, and read them before proposing edits. Explain planned changes briefly,
then use tools to make targeted changes. After modifications, inspect the diff. Run appropriate
tests/build checks when available. If a command fails, analyze the actual error instead of
guessing. Never claim a build or test succeeded unless a tool result confirms it. Never expose
secrets or credentials. Never perform destructive operations without explicit user confirmation.
Keep changes minimal and maintain existing architecture unless asked to redesign. When finished,
summarize: what changed, files changed, tests/build performed, and remaining issues.
""".trim()
    }
    suspend fun send(config: AIProviderConfig, history: List<ChatMessageDto>, isOnline: Boolean): AIClientResult =
        client.sendChat(config=config, systemPrompt=SYSTEM_PROMPT, history=history, isNetworkAvailable=isOnline)
}
