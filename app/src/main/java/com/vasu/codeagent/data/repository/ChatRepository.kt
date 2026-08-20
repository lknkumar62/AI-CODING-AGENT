package com.vasu.codeagent.data.repository

import com.vasu.codeagent.ai.provider.AIClientResult
import com.vasu.codeagent.ai.provider.AIProviderConfig
import com.vasu.codeagent.ai.provider.ChatFunctionDefinition
import com.vasu.codeagent.ai.provider.ChatMessageDto
import com.vasu.codeagent.ai.provider.ChatToolDefinition
import com.vasu.codeagent.ai.provider.OpenAICompatibleClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ChatRepository(private val client: OpenAICompatibleClient = OpenAICompatibleClient()) {
    companion object {
        val SYSTEM_PROMPT = """
You are VASU CODE AGENT, an autonomous Android coding agent with a real GitHub bridge.

Your job is to complete the user's requested coding task, not merely explain code.
You have real tools for listing repositories, inspecting files, creating/updating files,
and deleting files. Tool results are authoritative: never claim an operation succeeded
unless the tool result says it succeeded.

WORKFLOW:
1. Identify the target repository. If the user gives a repo URL/name, use it. Otherwise use list_repos.
2. Inspect the repository before making multi-file changes.
3. Read every existing file before editing it so you have its current SHA.
4. Make complete, production-ready file changes using write_file.
5. After each write, continue inspecting related files and fix inconsistencies.
6. If a tool returns an error, diagnose the returned error and retry with corrected arguments.
7. Keep iterating until the requested implementation is complete or a real external limitation prevents it.
8. Never invent a build/test result. This agent can edit GitHub files but can only know a GitHub Actions
   result when such a result is actually supplied to it.
9. Do not expose, request, or repeat secrets, tokens, or API keys.
10. For write/delete operations, the app may require user approval. Wait for the app's approval rather
    than pretending the operation happened.

Use native function/tool calls whenever a tool is needed. Do not emit <tool_call> XML or fake tool syntax.
When no tool is needed, answer in concise Markdown.
""".trimIndent()

        val TOOLS: List<ChatToolDefinition> = listOf(
            tool("list_repos", "List repositories accessible to the connected GitHub account.", emptyParams()),
            tool(
                "repo_info",
                "Get repository metadata and its default branch.",
                objectParams(listOf("repo"), mapOf("repo" to "Repository owner/name")),
            ),
            tool(
                "list_directory",
                "List files and directories at a repository path.",
                objectParams(
                    listOf("repo"),
                    mapOf(
                        "repo" to "Repository owner/name",
                        "path" to "Directory path; use an empty string for repository root",
                        "branch" to "Branch name; omit for default branch",
                    ),
                ),
            ),
            tool(
                "read_file",
                "Read a text file and return its current blob SHA. Always use this before editing an existing file.",
                objectParams(
                    listOf("repo", "path"),
                    mapOf(
                        "repo" to "Repository owner/name",
                        "path" to "File path",
                        "branch" to "Branch name; omit for default branch",
                    ),
                ),
            ),
            tool(
                "write_file",
                "Create a new file or replace an existing file and commit it. Existing files require the current SHA from read_file.",
                objectParams(
                    listOf("repo", "path", "content", "commit_message"),
                    mapOf(
                        "repo" to "Repository owner/name",
                        "path" to "File path",
                        "branch" to "Branch name",
                        "content" to "Complete UTF-8 file content",
                        "sha" to "Current blob SHA for an existing file; omit for a new file",
                        "commit_message" to "Short descriptive commit message",
                    ),
                ),
            ),
            tool(
                "delete_file",
                "Delete an existing file and commit the deletion. Requires its current SHA.",
                objectParams(
                    listOf("repo", "path", "sha", "commit_message"),
                    mapOf(
                        "repo" to "Repository owner/name",
                        "path" to "File path",
                        "branch" to "Branch name",
                        "sha" to "Current blob SHA returned by read_file",
                        "commit_message" to "Short descriptive commit message",
                    ),
                ),
            ),
        )

        private fun tool(name: String, description: String, parameters: JsonObject) =
            ChatToolDefinition(function = ChatFunctionDefinition(name, description, parameters))

        private fun emptyParams(): JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {}
            put("required", buildJsonArray {})
            put("additionalProperties", JsonPrimitive(false))
        }

        private fun objectParams(required: List<String>, properties: Map<String, String>): JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                properties.forEach { (key, description) ->
                    putJsonObject(key) {
                        put("type", "string")
                        put("description", description)
                    }
                }
            }
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
            put("additionalProperties", JsonPrimitive(false))
        }
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
        tools = TOOLS,
    )
}
