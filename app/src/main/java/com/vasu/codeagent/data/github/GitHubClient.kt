package com.vasu.codeagent.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Talks to the GitHub REST API (v3, "Contents API") using a user-supplied
 * Personal Access Token. Chosen over full OAuth because OAuth's redirect
 * flow needs a backend server this client-only app doesn't have — a PAT
 * gives the same scoped, revocable access without one.
 *
 * The token is passed in per-call (never cached in this class) and is
 * redacted before anything reaches Logcat. Every dangerous operation
 * (write, delete) is a distinct method the UI gates behind a confirmation
 * dialog — this class itself performs no confirmation.
 */
class GitHubClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val logging = HttpLoggingInterceptor { message ->
        val redacted = message.replace(Regex("token [A-Za-z0-9._-]+"), "token [REDACTED]")
            .replace(Regex("Bearer [A-Za-z0-9._-]+"), "Bearer [REDACTED]")
        android.util.Log.d("VasuGitHub", redacted)
    }.apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private fun headers(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    private suspend fun rawRequest(
        url: String,
        token: String,
        method: String = "GET",
        jsonBody: String? = null,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers(token).forEach { (k, v) -> builder.addHeader(k, v) }
        when (method) {
            "GET" -> builder.get()
            "PUT" -> builder.put((jsonBody ?: "{}").toRequestBody("application/json".toMediaType()))
            "DELETE" -> builder.delete((jsonBody ?: "{}").toRequestBody("application/json".toMediaType()))
            else -> builder.method(method, jsonBody?.toRequestBody("application/json".toMediaType()))
        }
        http.newCall(builder.build()).execute().use { resp ->
            resp.code to (resp.body?.string().orEmpty())
        }
    }

    suspend fun listMyRepos(token: String): GitHubOpResult<List<RepoSummary>> {
        val (code, body) = rawRequest(
            "https://api.github.com/user/repos?sort=updated&per_page=50",
            token,
        )
        if (code !in 200..299) return failure(code, body)
        val repos = json.parseToJsonElement(body).jsonArray.map { el ->
            val o = el.jsonObject
            RepoSummary(
                fullName = o["full_name"]!!.jsonPrimitive.content,
                defaultBranch = o["default_branch"]?.jsonPrimitive?.content ?: "main",
                isPrivate = o["private"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            )
        }
        return GitHubOpResult.Success(repos)
    }

    suspend fun getRepo(token: String, owner: String, repo: String): GitHubOpResult<RepoSummary> {
        val (code, body) = rawRequest("https://api.github.com/repos/$owner/$repo", token)
        if (code !in 200..299) return failure(code, body)
        val o = json.parseToJsonElement(body).jsonObject
        return GitHubOpResult.Success(
            RepoSummary(
                fullName = o["full_name"]!!.jsonPrimitive.content,
                defaultBranch = o["default_branch"]?.jsonPrimitive?.content ?: "main",
                isPrivate = o["private"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            ),
        )
    }

    /** Lists a folder's entries. GitHub returns a JSON array for a directory path. */
    suspend fun listDirectory(
        token: String, owner: String, repo: String, path: String, ref: String,
    ): GitHubOpResult<List<GitHubEntry>> {
        val url = contentsUrl(owner, repo, path, ref)
        val (code, body) = rawRequest(url, token)
        if (code !in 200..299) return failure(code, body)
        val element = json.parseToJsonElement(body)
        if (element !is JsonArray) return GitHubOpResult.Failure(code, "Path is a file, not a folder.")
        val entries = element.map { el ->
            val o = el.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val entryPath = o["path"]!!.jsonPrimitive.content
            when (o["type"]?.jsonPrimitive?.content) {
                "dir" -> GitHubEntry.Dir(name, entryPath)
                else -> GitHubEntry.RegularFile(
                    name = name,
                    path = entryPath,
                    sha = o["sha"]!!.jsonPrimitive.content,
                    sizeBytes = o["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                )
            }
        }.sortedWith(compareBy({ it !is GitHubEntry.Dir }, { it.name.lowercase() }))
        return GitHubOpResult.Success(entries)
    }

    /** Reads a single file's decoded text content plus its blob sha (needed to later update/delete it). */
    suspend fun getFile(
        token: String, owner: String, repo: String, path: String, ref: String,
    ): GitHubOpResult<FileContent> {
        val url = contentsUrl(owner, repo, path, ref)
        val (code, body) = rawRequest(url, token)
        if (code !in 200..299) return failure(code, body)
        val element = json.parseToJsonElement(body)
        if (element !is JsonObject) return GitHubOpResult.Failure(code, "Path is a folder, not a file.")
        val sha = element["sha"]!!.jsonPrimitive.content
        val encoded = element["content"]?.jsonPrimitive?.content.orEmpty().replace("\n", "")
        val text = if (encoded.isEmpty()) "" else String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        return GitHubOpResult.Success(FileContent(path, sha, text))
    }

    /**
     * Creates a new file or updates an existing one (pass [existingSha] for updates,
     * null to create). This single Contents-API call reads, commits, and pushes to
     * [branch] in one step — there is no separate local "push" step on GitHub's side.
     */
    suspend fun saveFile(
        token: String, owner: String, repo: String, path: String, branch: String,
        commitMessage: String, newText: String, existingSha: String?,
    ): GitHubOpResult<String> {
        val encoded = Base64.getEncoder().encodeToString(newText.toByteArray(Charsets.UTF_8))
        val shaField = if (existingSha != null) ",\"sha\":${jsonString(existingSha)}" else ""
        val jsonBody = """{"message":${jsonString(commitMessage)},"content":${jsonString(encoded)},"branch":${jsonString(branch)}$shaField}"""
        val url = contentsUrl(owner, repo, path, ref = null)
        val (code, respBody) = rawRequest(url, token, method = "PUT", jsonBody = jsonBody)
        if (code !in 200..299) return failure(code, respBody)
        val newSha = json.parseToJsonElement(respBody).jsonObject["content"]
            ?.jsonObject?.get("sha")?.jsonPrimitive?.content ?: existingSha.orEmpty()
        return GitHubOpResult.Success(newSha)
    }

    /** Deletes a file. The caller (UI) must have already gotten explicit user confirmation. */
    suspend fun deleteFile(
        token: String, owner: String, repo: String, path: String, branch: String,
        commitMessage: String, sha: String,
    ): GitHubOpResult<Unit> {
        val jsonBody = """{"message":${jsonString(commitMessage)},"sha":${jsonString(sha)},"branch":${jsonString(branch)}}"""
        val url = contentsUrl(owner, repo, path, ref = null)
        val (code, body) = rawRequest(url, token, method = "DELETE", jsonBody = jsonBody)
        if (code !in 200..299) return failure(code, body)
        return GitHubOpResult.Success(Unit)
    }

    private fun contentsUrl(owner: String, repo: String, path: String, ref: String?): String {
        val encodedPath = path.split("/").filter { it.isNotEmpty() }
            .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val base = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath"
        return if (ref != null) "$base?ref=${java.net.URLEncoder.encode(ref, "UTF-8")}" else base
    }

    private fun jsonString(s: String): String {
        val escaped = buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }
        return escaped
    }

    private fun failure(code: Int, body: String): GitHubOpResult.Failure {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull() ?: "GitHub request failed (HTTP $code)."
        return GitHubOpResult.Failure(code, message)
    }
}
