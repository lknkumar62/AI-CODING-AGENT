package com.vasu.codeagent.ui.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vasu.codeagent.data.github.GitHubEntry

@Composable
fun GitHubScreen(viewModel: GitHubViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.mode) {
            GitHubScreenMode.RepoPicker -> RepoPickerBody(state, viewModel)
            GitHubScreenMode.Browser -> BrowserBody(state, viewModel)
            GitHubScreenMode.Editor -> EditorBody(state, viewModel)
        }

        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) {
                        Text("Dismiss", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }

        state.pendingDelete?.let { pending ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete file?") },
                text = { Text("This permanently deletes\n${pending.path}\nfrom the ${state.branch} branch. This cannot be undone from the app.") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun RepoPickerBody(state: GitHubUiState, viewModel: GitHubViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("GitHub", style = MaterialTheme.typography.titleLarge)
        Text(
            "Open a repository to browse, read, edit, create, and delete files — every save commits and pushes straight to GitHub.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.ownerRepoInput,
            onValueChange = viewModel::onOwnerRepoInputChange,
            label = { Text("owner/repo") },
            placeholder = { Text("lknkumar62/AI-CODING-AGENT") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.openRepo() }, modifier = Modifier.fillMaxWidth()) {
            Text("Open repository")
        }
        OutlinedButton(onClick = { viewModel.loadMyRepos() }, modifier = Modifier.fillMaxWidth()) {
            Text("List my repositories")
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }

        if (state.myRepos.isNotEmpty()) {
            Text("Your repositories", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.myRepos) { fullName ->
                    Surface(
                        onClick = { viewModel.onOwnerRepoInputChange(fullName); viewModel.openRepo(fullName) },
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(fullName, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserBody(state: GitHubUiState, viewModel: GitHubViewModel) {
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.currentPath.isNotBlank()) {
                        IconButton(onClick = viewModel::navigateUp) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                    Column {
                        Text("${state.repoLabel} · ${state.branch}", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (state.currentPath.isBlank()) "/" else "/${state.currentPath}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                IconButton(onClick = { showNewFileDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New file")
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(state.entries) { entry ->
                    when (entry) {
                        is GitHubEntry.Dir -> Surface(
                            onClick = { viewModel.loadDirectory(entry.path) },
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(entry.name, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            }
                        }
                        is GitHubEntry.RegularFile -> Surface(
                            onClick = { viewModel.openFile(entry) },
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(entry.name, modifier = Modifier.padding(start = 12.dp))
                                }
                                IconButton(onClick = { viewModel.requestDelete(entry) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${entry.name}")
                                }
                            }
                        }
                    }
                }
                if (state.entries.isEmpty()) {
                    item {
                        Text(
                            "Empty folder. Tap + to create a file here.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = viewModel::closeRepo, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Close repository")
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New file") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("NewFile.kt") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        viewModel.startNewFile(newFileName.trim())
                        showNewFileDialog = false
                        newFileName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorBody(state: GitHubUiState, viewModel: GitHubViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::closeEditor) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.isNewFile) "New file" else "Editing",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(state.editorPath, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.isDirty) {
                    Text("Unsaved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = state.editorText,
            onValueChange = viewModel::onEditorTextChange,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.commitMessage,
                onValueChange = viewModel::onCommitMessageChange,
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::saveFile,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Commit & push")
                }
            }
        }
    }
}
