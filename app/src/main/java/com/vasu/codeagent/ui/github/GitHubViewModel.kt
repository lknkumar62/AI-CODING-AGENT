package com.vasu.codeagent.ui.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.data.github.GitHubEntry
import com.vasu.codeagent.data.github.GitHubOpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen is currently showing. */
sealed interface GitHubScreenMode {
    /** No repo opened yet — user is picking/typing one. */
    data object RepoPicker : GitHubScreenMode
    /** Browsing a folder's contents. */
    data object Browser : GitHubScreenMode
    /** Viewing/editing a single file. */
    data object Editor : GitHubScreenMode
}

data class PendingDelete(val path: String, val sha: String)

data class GitHubUiState(
    val mode: GitHubScreenMode = GitHubScreenMode.RepoPicker,
    val isLoading: Boolean = false,
    val error: String? = null,

    val ownerRepoInput: String = "",
    val myRepos: List<String> = emptyList(),

    val owner: String = "",
    val repo: String = "",
    val branch: String = "",
    val currentPath: String = "", // "" = repo root

    val entries: List<GitHubEntry> = emptyList(),

    // Editor state
    val editorPath: String = "",
    val editorSha: String? = null, // null => creating a new file
    val editorText: String = "",
    val editorOriginalText: String = "",
    val commitMessage: String = "",
    val isSaving: Boolean = false,

    val pendingDelete: PendingDelete? = null,
) {
    val isNewFile get() = editorSha == null
    val isDirty get() = editorText != editorOriginalText
    val repoLabel get() = if (owner.isNotBlank()) "$owner/$repo" else ""
}

class GitHubViewModel(private val app: VasuApp) : ViewModel() {

    private val _state = MutableStateFlow(GitHubUiState())
    val state: StateFlow<GitHubUiState> = _state

    init {
        val savedRepo = app.settingsStore.lastRepo.value
        _state.update { it.copy(ownerRepoInput = savedRepo) }
    }

    private fun token(): String = app.settingsStore.githubToken.value

    fun onOwnerRepoInputChange(text: String) {
        _state.update { it.copy(ownerRepoInput = text) }
    }

    fun loadMyRepos() {
        val tok = token()
        if (tok.isBlank()) {
            _state.update { it.copy(error = "Add a GitHub token in Settings first.") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = app.gitHubRepository.listMyRepos(tok)) {
                is GitHubOpResult.Success -> _state.update {
                    it.copy(isLoading = false, myRepos = result.value.map { r -> r.fullName })
                }
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun openRepo(fullName: String = _state.value.ownerRepoInput) {
        val tok = token()
        if (tok.isBlank()) {
            _state.update { it.copy(error = "Add a GitHub token in Settings first.") }
            return
        }
        val parts = fullName.trim().trim('/').split("/")
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            _state.update { it.copy(error = "Enter a repo as owner/repo, e.g. lknkumar62/AI-CODING-AGENT") }
            return
        }
        val (owner, repo) = parts
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = app.gitHubRepository.getRepo(tok, owner, repo)) {
                is GitHubOpResult.Success -> {
                    app.settingsStore.saveLastRepo("$owner/$repo")
                    _state.update {
                        it.copy(
                            owner = owner, repo = repo, branch = result.value.defaultBranch,
                            currentPath = "", isLoading = false,
                        )
                    }
                    loadDirectory("")
                }
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun loadDirectory(path: String) {
        val s = _state.value
        val tok = token()
        _state.update { it.copy(isLoading = true, error = null, currentPath = path, mode = GitHubScreenMode.Browser) }
        viewModelScope.launch {
            when (val result = app.gitHubRepository.listDirectory(tok, s.owner, s.repo, path, s.branch)) {
                is GitHubOpResult.Success -> _state.update {
                    it.copy(isLoading = false, entries = result.value)
                }
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun navigateUp() {
        val s = _state.value
        if (s.currentPath.isBlank()) return
        val parent = s.currentPath.substringBeforeLast("/", missingDelimiterValue = "")
        loadDirectory(parent)
    }

    fun openFile(entry: GitHubEntry.RegularFile) {
        val s = _state.value
        val tok = token()
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = app.gitHubRepository.getFile(tok, s.owner, s.repo, entry.path, s.branch)) {
                is GitHubOpResult.Success -> _state.update {
                    it.copy(
                        isLoading = false, mode = GitHubScreenMode.Editor,
                        editorPath = result.value.path, editorSha = result.value.sha,
                        editorText = result.value.text, editorOriginalText = result.value.text,
                        commitMessage = "Update ${entry.name} via VASU CODE AGENT",
                    )
                }
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun startNewFile(fileName: String) {
        val s = _state.value
        val path = if (s.currentPath.isBlank()) fileName else "${s.currentPath}/$fileName"
        _state.update {
            it.copy(
                mode = GitHubScreenMode.Editor,
                editorPath = path, editorSha = null,
                editorText = "", editorOriginalText = "",
                commitMessage = "Create $fileName via VASU CODE AGENT",
            )
        }
    }

    fun onEditorTextChange(text: String) {
        _state.update { it.copy(editorText = text) }
    }

    fun onCommitMessageChange(text: String) {
        _state.update { it.copy(commitMessage = text) }
    }

    fun saveFile() {
        val s = _state.value
        val tok = token()
        if (s.commitMessage.isBlank()) {
            _state.update { it.copy(error = "Add a short commit message.") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = app.gitHubRepository.saveFile(
                tok, s.owner, s.repo, s.editorPath, s.branch,
                s.commitMessage, s.editorText, s.editorSha,
            )
            when (result) {
                is GitHubOpResult.Success -> {
                    _state.update {
                        it.copy(
                            isSaving = false, editorSha = result.value,
                            editorOriginalText = it.editorText, mode = GitHubScreenMode.Browser,
                        )
                    }
                    loadDirectory(s.currentPath)
                }
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    /** Step 1: the UI must call this only after the user taps "Delete". It just asks for confirmation. */
    fun requestDelete(entry: GitHubEntry.RegularFile) {
        _state.update { it.copy(pendingDelete = PendingDelete(entry.path, entry.sha)) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /** Step 2: only runs after the user explicitly confirms in the dialog. */
    fun confirmDelete() {
        val s = _state.value
        val pending = s.pendingDelete ?: return
        val tok = token()
        _state.update { it.copy(isLoading = true, pendingDelete = null, error = null) }
        viewModelScope.launch {
            val result = app.gitHubRepository.deleteFile(
                tok, s.owner, s.repo, pending.path, s.branch,
                "Delete ${pending.path.substringAfterLast('/')} via VASU CODE AGENT", pending.sha,
            )
            when (result) {
                is GitHubOpResult.Success -> loadDirectory(s.currentPath)
                is GitHubOpResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun closeEditor() {
        _state.update { it.copy(mode = GitHubScreenMode.Browser) }
    }

    fun closeRepo() {
        _state.update { GitHubUiState(ownerRepoInput = it.ownerRepoInput) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(app: VasuApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GitHubViewModel(app) as T
        }
    }
}
