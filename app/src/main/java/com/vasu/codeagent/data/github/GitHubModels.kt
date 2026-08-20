package com.vasu.codeagent.data.github

/**
 * Domain-level models used by the GitHub browser/editor UI.
 * Kept separate from the raw JSON parsing in GitHubClient so the UI layer
 * never has to think about GitHub's slightly different response shapes
 * for files vs. directories vs. repo lists.
 */

data class RepoSummary(
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
)

sealed interface GitHubEntry {
    val name: String
    val path: String

    data class Dir(override val name: String, override val path: String) : GitHubEntry
    data class RegularFile(
        override val name: String,
        override val path: String,
        val sha: String,
        val sizeBytes: Long,
    ) : GitHubEntry
}

data class FileContent(
    val path: String,
    val sha: String,
    val text: String,
)

sealed interface GitHubOpResult<out T> {
    data class Success<T>(val value: T) : GitHubOpResult<T>
    data class Failure(val httpCode: Int?, val message: String) : GitHubOpResult<Nothing>
}
