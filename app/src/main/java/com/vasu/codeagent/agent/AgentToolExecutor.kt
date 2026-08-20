package com.vasu.codeagent.agent

import com.vasu.codeagent.data.github.GitHubEntry
import com.vasu.codeagent.data.github.GitHubOpResult
import com.vasu.codeagent.data.repository.GitHubRepository
import com.vasu.codeagent.data.settings.SecureSettingsStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Executes the explicit GitHub tool surface exposed to the coding model. */
class AgentToolExecutor(
    private val github: GitHubRepository,
    private val settings: SecureSettingsStore,
) {
    data class Result(val ok: Boolean, val text: String)

    suspend fun execute(name: String, args: JsonObject): Result {
        val token = settings.githubToken.value
        if (token.isBlank()) return Result(false, "GitHub token is missing. Save it in Settings first.")

        if (name == "list_repos") {
            return when (val r = github.listMyRepos(token)) {
                is GitHubOpResult.Success -> Result(true, r.value.joinToString("\n") { "${it.fullName} (branch=${it.defaultBranch}, private=${it.isPrivate})" })
                is GitHubOpResult.Failure -> Result(false, r.message)
            }
        }

        val repo = arg(args, "repo")?.takeIf { it.isNotBlank() } ?: settings.lastRepo.value
        if (repo.isNullOrBlank() || !repo.contains('/')) {
            return Result(false, "Repository is not selected. Use list_repos or provide repo as owner/name.")
        }
        val owner = repo.substringBefore('/')
        val namePart = repo.substringAfter('/')
        val branch = arg(args, "branch")?.takeIf { it.isNotBlank() }
            ?: when (val r = github.getRepo(token, owner, namePart)) {
                is GitHubOpResult.Success -> r.value.defaultBranch
                is GitHubOpResult.Failure -> return Result(false, r.message)
            }
        settings.saveLastRepo(repo)

        return try {
            when (name) {
                "repo_info" -> when (val r = github.getRepo(token, owner, namePart)) {
                    is GitHubOpResult.Success -> Result(true, "repo=${r.value.fullName}\ndefault_branch=${r.value.defaultBranch}\nprivate=${r.value.isPrivate}")
                    is GitHubOpResult.Failure -> Result(false, r.message)
                }
                "list_directory" -> when (val r = github.listDirectory(token, owner, namePart, arg(args, "path") ?: "", branch)) {
                    is GitHubOpResult.Success -> Result(true, formatEntries(r.value))
                    is GitHubOpResult.Failure -> Result(false, r.message)
                }
                "read_file" -> when (val r = github.getFile(token, owner, namePart, required(args, "path"), branch)) {
                    is GitHubOpResult.Success -> Result(true, "FILE: ${r.value.path}\nSHA: ${r.value.sha}\n\n${r.value.text}")
                    is GitHubOpResult.Failure -> Result(false, r.message)
                }
                "write_file" -> when (val r = github.saveFile(
                    token, owner, namePart, required(args, "path"), branch,
                    required(args, "commit_message"), required(args, "content"), arg(args, "sha"),
                )) {
                    is GitHubOpResult.Success -> Result(true, "Committed ${required(args, "path")} to $repo@$branch. New SHA: ${r.value}")
                    is GitHubOpResult.Failure -> Result(false, r.message)
                }
                "delete_file" -> when (val r = github.deleteFile(
                    token, owner, namePart, required(args, "path"), branch,
                    required(args, "commit_message"), required(args, "sha"),
                )) {
                    is GitHubOpResult.Success -> Result(true, "Deleted ${required(args, "path")} from $repo@$branch.")
                    is GitHubOpResult.Failure -> Result(false, r.message)
                }
                else -> Result(false, "Unknown tool: $name")
            }
        } catch (e: IllegalArgumentException) {
            Result(false, e.message ?: "Invalid tool arguments")
        }
    }

    fun isSafe(name: String): Boolean = name in setOf("list_repos", "repo_info", "list_directory", "read_file")

    private fun arg(args: JsonObject, key: String): String? = args[key]?.jsonPrimitive?.contentOrNull
    private fun required(args: JsonObject, key: String): String = arg(args, key)?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required argument: $key")

    private fun formatEntries(entries: List<GitHubEntry>): String = entries.joinToString("\n") {
        when (it) {
            is GitHubEntry.Dir -> "DIR  ${it.path}"
            is GitHubEntry.RegularFile -> "FILE ${it.path}  sha=${it.sha}  size=${it.sizeBytes}"
        }
    }
}
