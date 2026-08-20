package com.vasu.codeagent.data.repository

import com.vasu.codeagent.data.github.FileContent
import com.vasu.codeagent.data.github.GitHubClient
import com.vasu.codeagent.data.github.GitHubEntry
import com.vasu.codeagent.data.github.GitHubOpResult
import com.vasu.codeagent.data.github.RepoSummary

/**
 * Thin facade over [GitHubClient] so the ViewModel doesn't reach into the
 * network layer directly. Every method still requires an explicit token —
 * this class holds no state and caches nothing.
 */
class GitHubRepository(private val client: GitHubClient = GitHubClient()) {

    suspend fun listMyRepos(token: String): GitHubOpResult<List<RepoSummary>> = client.listMyRepos(token)

    suspend fun getRepo(token: String, owner: String, repo: String): GitHubOpResult<RepoSummary> =
        client.getRepo(token, owner, repo)

    suspend fun listDirectory(
        token: String, owner: String, repo: String, path: String, branch: String,
    ): GitHubOpResult<List<GitHubEntry>> = client.listDirectory(token, owner, repo, path, branch)

    suspend fun getFile(
        token: String, owner: String, repo: String, path: String, branch: String,
    ): GitHubOpResult<FileContent> = client.getFile(token, owner, repo, path, branch)

    suspend fun saveFile(
        token: String, owner: String, repo: String, path: String, branch: String,
        commitMessage: String, newText: String, existingSha: String?,
    ): GitHubOpResult<String> =
        client.saveFile(token, owner, repo, path, branch, commitMessage, newText, existingSha)

    suspend fun deleteFile(
        token: String, owner: String, repo: String, path: String, branch: String,
        commitMessage: String, sha: String,
    ): GitHubOpResult<Unit> = client.deleteFile(token, owner, repo, path, branch, commitMessage, sha)
}
