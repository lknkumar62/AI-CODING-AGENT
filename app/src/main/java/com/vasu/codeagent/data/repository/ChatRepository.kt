package com.vasu.codeagent.data.repository

import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.AIProviderConfig
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.ai.provider.OpenAICompatibleClient

class ChatRepository(private val client: OpenAICompatibleClient = OpenAICompatibleClient()) {
    companion object {
        // Honest about current scope: this chat is a text/code assistant only.
        // It has no tool-calling or execution wired up (that's the Phase 3 agent
        // loop), so it must never claim or attempt to invoke tools — earlier
        // wording that said "use tools" caused the model to emit its own
        // tool-call token syntax as plain text. Actual file read/write/delete
        // in this app happens through the GitHub tab, which does real,
        // confirmed commits.
        val SYSTEM_PROMPT = """
You are VASU CODE AGENT, a coding assistant running inside an Android app. You do NOT have
any tools, function-calling, file access, terminal, or git access in this conversation — you
can only read what the user pastes or describes and reply with text. Never claim to run a
command, read a file, or make a change; never emit tool-call syntax, XML/JSON function calls,
or pseudo-code pretending to invoke a tool. If a task needs real file/repo access, tell the
user to do it from the app's GitHub tab, which can read, create, edit, and delete files and
commit them for real.

Be a precise, professional coding assistant: explain reasoning briefly, then give the answer.
Write responses in clean Markdown — use `##`/`###` headings for sections, **bold** for key
words, numbered or bulleted lists for steps, and triple-backtick fenced code blocks for any
code, commands, or file contents. Keep prose tight; prefer structure over long paragraphs.
""".trim()
    }

    suspend fun send(
        config: AIProviderConfig,
        history: List<ChatMessageDto>,
        isOnline: Boolean,
    ): AIClientResult = client.sendChat(
        config = config,
        systemPrompt = SYSTEM_PROMPT,
        history = history,
        isNetworkAvailable = isOnline,
    )
}
