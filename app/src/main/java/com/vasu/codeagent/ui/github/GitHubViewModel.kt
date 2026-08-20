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

sealed interface GitHubScreenMode { data object RepoPicker : GitHubScreenMode; data object Browser : GitHubScreenMode; data object Editor : GitHubScreenMode }
data class PendingDelete(val path: String, val sha: String)
data class GitHubUiState(
    val mode: GitHubScreenMode = GitHubScreenMode.RepoPicker, val isLoading: Boolean = false, val error: String? = null,
    val ownerRepoInput: String = "", val myRepos: List<String> = emptyList(), val owner: String = "", val repo: String = "", val branch: String = "", val currentPath: String = "",
    val entries: List<GitHubEntry> = emptyList(), val editorPath: String = "", val editorSha: String? = null, val editorText: String = "", val editorOriginalText: String = "", val commitMessage: String = "", val isSaving: Boolean = false,
    val pendingDelete: PendingDelete? = null,
) { val isNewFile get() = editorSha == null; val isDirty get() = editorText != editorOriginalText; val repoLabel get() = if (owner.isNotBlank()) "$owner/$repo" else "" }

class GitHubViewModel(private val app: VasuApp) : ViewModel() {
    private val _state = MutableStateFlow(GitHubUiState()); val state: StateFlow<GitHubUiState> = _state
    init { _state.update { it.copy(ownerRepoInput = app.settingsStore.lastRepo.value) } }
    private fun token() = app.settingsStore.githubToken.value
    fun onOwnerRepoInputChange(text: String) { _state.update { it.copy(ownerRepoInput = text) } }
    fun loadMyRepos() { val tok=token(); if(tok.isBlank()){_state.update{it.copy(error="Add a GitHub token in Settings first.")};return}; _state.update{it.copy(isLoading=true,error=null)}; viewModelScope.launch { when(val r=app.gitHubRepository.listMyRepos(tok)){is GitHubOpResult.Success->_state.update{it.copy(isLoading=false,myRepos=r.value.map{it.fullName})};is GitHubOpResult.Failure->_state.update{it.copy(isLoading=false,error=r.message)}} } }
    fun openRepo(fullName:String=_state.value.ownerRepoInput){val tok=token();if(tok.isBlank()){_state.update{it.copy(error="Add a GitHub token in Settings first.")};return};val p=fullName.trim().trim('/').split('/');if(p.size!=2||p.any{it.isBlank()}){_state.update{it.copy(error="Enter a repo as owner/repo, e.g. lknkumar62/AI-CODING-AGENT")};return};val(owner,repo)=p;_state.update{it.copy(isLoading=true,error=null)};viewModelScope.launch{when(val r=app.gitHubRepository.getRepo(tok,owner,repo)){is GitHubOpResult.Success->{app.settingsStore.saveLastRepo("$owner/$repo");_state.update{it.copy(owner=owner,repo=repo,branch=r.value.defaultBranch,currentPath="",isLoading=false)};loadDirectory("")};is GitHubOpResult.Failure->_state.update{it.copy(isLoading=false,error=r.message)}}}}
    fun loadDirectory(path:String){val s=_state.value;val tok=token();_state.update{it.copy(isLoading=true,error=null,currentPath=path,mode=GitHubScreenMode.Browser)};viewModelScope.launch{when(val r=app.gitHubRepository.listDirectory(tok,s.owner,s.repo,path,s.branch)){is GitHubOpResult.Success->_state.update{it.copy(isLoading=false,entries=r.value)};is GitHubOpResult.Failure->_state.update{it.copy(isLoading=false,error=r.message)}}}}
    fun navigateUp(){val s=_state.value;if(s.currentPath.isBlank())return;loadDirectory(s.currentPath.substringBeforeLast('/',missingDelimiterValue=""))}
    fun openFile(e:GitHubEntry.RegularFile){val s=_state.value;_state.update{it.copy(isLoading=true,error=null)};viewModelScope.launch{when(val r=app.gitHubRepository.getFile(token(),s.owner,s.repo,e.path,s.branch)){is GitHubOpResult.Success->_state.update{it.copy(isLoading=false,mode=GitHubScreenMode.Editor,editorPath=r.value.path,editorSha=r.value.sha,editorText=r.value.text,editorOriginalText=r.value.text,commitMessage="Update ${e.name} via VASU CODE AGENT")};is GitHubOpResult.Failure->_state.update{it.copy(isLoading=false,error=r.message)}}}}
    fun startNewFile(fileName:String){val s=_state.value;val path=if(s.currentPath.isBlank())fileName else "${s.currentPath}/$fileName";_state.update{it.copy(mode=GitHubScreenMode.Editor,editorPath=path,editorSha=null,editorText="",editorOriginalText="",commitMessage="Create $fileName via VASU CODE AGENT")}}
    fun onEditorTextChange(text:String){_state.update{it.copy(editorText=text)}};fun onCommitMessageChange(text:String){_state.update{it.copy(commitMessage=text)}}
    fun saveFile(){val s=_state.value;if(s.commitMessage.isBlank()){_state.update{it.copy(error="Add a short commit message.")};return};_state.update{it.copy(isSaving=true,error=null)};viewModelScope.launch{when(val r=app.gitHubRepository.saveFile(token(),s.owner,s.repo,s.editorPath,s.branch,s.commitMessage,s.editorText,s.editorSha)){is GitHubOpResult.Success->{_state.update{it.copy(isSaving=false,editorSha=r.value,editorOriginalText=it.editorText,mode=GitHubScreenMode.Browser)};loadDirectory(s.currentPath)};is GitHubOpResult.Failure->_state.update{it.copy(isSaving=false,error=r.message)}}}}
    fun requestDelete(e:GitHubEntry.RegularFile){_state.update{it.copy(pendingDelete=PendingDelete(e.path,e.sha))}};fun cancelDelete(){_state.update{it.copy(pendingDelete=null)}}
    fun confirmDelete(){val s=_state.value;val p=s.pendingDelete?:return;_state.update{it.copy(isLoading=true,pendingDelete=null,error=null)};viewModelScope.launch{when(val r=app.gitHubRepository.deleteFile(token(),s.owner,s.repo,p.path,s.branch,"Delete ${p.path.substringAfterLast('/')} via VASU CODE AGENT",p.sha)){is GitHubOpResult.Success->loadDirectory(s.currentPath);is GitHubOpResult.Failure->_state.update{it.copy(isLoading=false,error=r.message)}}}}
    fun closeEditor(){_state.update{it.copy(mode=GitHubScreenMode.Browser)}};fun closeRepo(){_state.update{GitHubUiState(ownerRepoInput=it.ownerRepoInput)}};fun dismissError(){_state.update{it.copy(error=null)}}
    companion object { fun factory(app:VasuApp)=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(c:Class<T>):T=GitHubViewModel(app) as T} }
}
