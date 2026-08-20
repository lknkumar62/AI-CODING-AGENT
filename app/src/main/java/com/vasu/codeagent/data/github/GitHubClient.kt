package com.vasu.codeagent.data.github

import android.util.Base64
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Small GitHub REST client used by the Android editor.
 * Tokens are supplied by the caller and are never persisted here.
 */
class GitHubClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val logging = HttpLoggingInterceptor { message ->
        val redacted = message
            .replace(Regex("Bearer\\s+[^\\s]+"), "Bearer [REDACTED]")
            .replace(Regex("token\\s+[^\\s]+"), "token [REDACTED]")
        android.util.Log.d("VasuGitHub", redacted)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private fun requestBuilder(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun rawRequest(
        url: String,
        token: String,
        method: String = "GET",
        body: String? = null,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val builder = requestBuilder(url, token)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        when (method) {
            "GET" -> builder.get()
            "PUT" -> builder.put((body ?: "{}").toRequestBody(mediaType))
            "DELETE" -> builder.delete((body ?: "{}").toRequestBody(mediaType))
            else -> builder.method(method, body?.toRequestBody(mediaType))
        }

        http.newCall(builder.build()).execute().use { response ->
            response.code to response.body?.string().orEmpty()
        }
    }

    suspend fun listMyRepos(token: String): GitHubOpResult<List<RepoSummary>> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        val (code, body) = rawRequest(
            "https://api.github.com/user/repos?sort=updated&per_page=50",
            token,
        )
        if (code !in 200..299) return failure(code, body)

        val root = runCatching { json.parseToJsonElement(body).jsonArray }
            .getOrElse { return GitHubOpResult.Failure(code, "Invalid GitHub response.") }

        val repos = root.mapNotNull { element ->
            val obj = element.jsonObject
            val fullName = obj["full_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val branch = obj["default_branch"]?.jsonPrimitive?.content ?: "main"
            val privateRepo = obj["private"]?.jsonPrimitive?.content?.toBoolean() ?: false
            RepoSummary(fullName, branch, privateRepo)
        }
        return GitHubOpResult.Success(repos)
    }

    suspend fun getRepo(
        token: String,
        owner: String,
        repo: String,
    ): GitHubOpResult<RepoSummary> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        val (code, body) = rawRequest("https://api.github.com/repos/$owner/$repo", token)
        if (code !in 200..299) return failure(code, body)

        val obj = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { return GitHubOpResult.Failure(code, "Invalid GitHub response.") }

        val fullName = obj["full_name"]?.jsonPrimitive?.content
            ?: return GitHubOpResult.Failure(code, "GitHub did not return repository information.")
        val branch = obj["default_branch"]?.jsonPrimitive?.content ?: "main"
        val privateRepo = obj["private"]?.jsonPrimitive?.content?.toBoolean() ?: false
        return GitHubOpResult.Success(RepoSummary(fullName, branch, privateRepo))
    }

    suspend fun listDirectory(
        token: String,
        owner: String,
        repo: String,
        path: String,
        ref: String,
    ): GitHubOpResult<List<GitHubEntry>> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        val (code, body) = rawRequest(contentsUrl(owner, repo, path, ref), token)
        if (code !in 200..299) return failure(code, body)

        val element = runCatching { json.parseToJsonElement(body) }
            .getOrElse { return GitHubOpResult.Failure(code, "Invalid GitHub response.") }
        if (element !is JsonArray) {
            return GitHubOpResult.Failure(code, "Path is a file, not a folder.")
        }

        val entries = element.mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val entryPath = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.content) {
                "dir" -> GitHubEntry.Dir(name, entryPath)
                "file" -> {
                    val sha = obj["sha"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    GitHubEntry.RegularFile(name, entryPath, sha, size)
                }
                else -> null
            }
        }.sortedWith(compareBy({ it !is GitHubEntry.Dir }, { it.name.lowercase() }))

        return GitHubOpResult.Success(entries)
    }

    suspend fun getFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        ref: String,
    ): GitHubOpResult<FileContent> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        val (code, body) = rawRequest(contentsUrl(owner, repo, path, ref), token)
        if (code !in 200..299) return failure(code, body)

        val obj = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { return GitHubOpResult.Failure(code, "Invalid GitHub response.") }

        val sha = obj["sha"]?.jsonPrimitive?.content
            ?: return GitHubOpResult.Failure(code, "GitHub did not return the file SHA.")
        val encoded = obj["content"]?.jsonPrimitive?.content.orEmpty()
            .replace("\n", "")
            .replace("\r", "")
        val text = if (encoded.isBlank()) {
            ""
        } else {
            runCatching {
                String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrElse {
                return GitHubOpResult.Failure(code, "Could not decode GitHub file content.")
            }
        }
        return GitHubOpResult.Success(FileContent(path, sha, text))
    }

    suspend fun saveFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String,
        commitMessage: String,
        newText: String,
        existingSha: String?,
    ): GitHubOpResult<String> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        if (path.isBlank()) return GitHubOpResult.Failure(null, "File path cannot be empty.")
        if (commitMessage.isBlank()) return GitHubOpResult.Failure(null, "Commit message cannot be empty.")

        val encoded = Base64.encodeToString(newText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val fields = buildString {
            append("{\"message\":")
            append(jsonString(commitMessage))
            append(",\"content\":")
            append(jsonString(encoded))
            append(",\"branch\":")
            append(jsonString(branch))
            if (existingSha != null) {
                append(",\"sha\":")
                append(jsonString(existingSha))
            }
            append('}')
        }

        val (code, body) = rawRequest(
            contentsUrl(owner, repo, path, null),
            token,
            "PUT",
            fields,
        )
        if (code !in 200..299) return failure(code, body)

        val newSha = runCatching {
            json.parseToJsonElement(body).jsonObject["content"]
                ?.jsonObject?.get("sha")?.jsonPrimitive?.content
        }.getOrNull()

        return GitHubOpResult.Success(newSha ?: existingSha.orEmpty())
    }

    suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String,
        commitMessage: String,
        sha: String,
    ): GitHubOpResult<Unit> {
        if (token.isBlank()) return GitHubOpResult.Failure(null, "GitHub token is missing.")
        val body = """{"message":${jsonString(commitMessage)},"sha":${jsonString(sha)},"branch":${jsonString(branch)}}"""
        val (code, responseBody) = rawRequest(
            contentsUrl(owner, repo, path, null),
            token,
            "DELETE",
            body,
        )
        if (code !in 200..299) return failure(code, responseBody)
        return GitHubOpResult.Success(Unit)
    }

    private fun contentsUrl(
        owner: String,
        repo: String,
        path: String,
        ref: String?,
    ): String {
        val encodedPath = path
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val base = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath"
        return if (ref.isNullOrBlank()) {
            base
        } else {
            "$base?ref=${URLEncoder.encode(ref, "UTF-8")}"
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u%04x".format(c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    private fun failure(code: Int, body: String): GitHubOpResult.Failure {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return GitHubOpResult.Failure(code, message ?: "GitHub request failed (HTTP $code).")
    }
}
