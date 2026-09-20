package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubGitRef
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

sealed interface RepositoryBranchWrite {
    data class Create(val branch: String, val sourceRef: String) : RepositoryBranchWrite
    data class Delete(val branch: String) : RepositoryBranchWrite
    data class Rename(val from: String, val to: String) : RepositoryBranchWrite
}

sealed interface RepositoryBranchWriteOutcome {
    data class Succeeded(val write: RepositoryBranchWrite) : RepositoryBranchWriteOutcome
    data class Failed(
        val write: RepositoryBranchWrite,
        val failure: RepositoryWriteFailure
    ) : RepositoryBranchWriteOutcome
}

sealed interface RepositoryBranchesAction {
    data object Refresh : RepositoryBranchesAction
    data object Retry : RepositoryBranchesAction
    data object LoadMore : RepositoryBranchesAction
    data object DismissWriteOutcome : RepositoryBranchesAction
    data class Search(val query: String) : RepositoryBranchesAction
    data class CreateBranch(val branch: String, val sourceRef: String) : RepositoryBranchesAction
    data class DeleteBranch(val branch: String) : RepositoryBranchesAction
    data class RenameBranch(val from: String, val to: String) : RepositoryBranchesAction
}

@Immutable
data class RepositoryBranchesUiState(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val items: List<GithubBranch> = emptyList(),
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val pendingWrite: RepositoryBranchWrite? = null,
    val writeOutcome: RepositoryBranchWriteOutcome? = null,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
) {
    val fullName: String get() = "$owner/$name"
    val filteredItems: List<GithubBranch>
        get() = query.trim().takeIf(String::isNotBlank)?.let { normalized ->
            items.filter { it.name.contains(normalized, ignoreCase = true) }
        } ?: items
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore

    /** Creating, deleting and renaming branches both require push access. */
    val canWrite: Boolean get() = viewerLogin != null && viewerRole.canPush
}

class RepositoryBranchesViewModel(
    private val owner: String,
    private val name: String,
    defaultBranch: String,
    private val repository: GithubRepositoryContentsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryBranchesUiState(owner, name, defaultBranch))
    val state: StateFlow<RepositoryBranchesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null

    init {
        load(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login == sessionLogin) return
        sessionLogin = login
        _state.update { it.copy(viewerLogin = login) }
        loadViewerRole(signedIn = login != null)
    }

    private fun loadViewerRole(signedIn: Boolean) {
        viewerRoleJob?.cancel()
        if (!signedIn) {
            _state.update { it.copy(viewerRole = GithubCollaboratorRole.UNKNOWN) }
            return
        }
        viewerRoleJob = viewModelScope.launch {
            val role = detailsRepository.details(owner, name)
                .getOrNull()
                ?.viewerRole
                ?: GithubCollaboratorRole.UNKNOWN
            _state.update { it.copy(viewerRole = role) }
        }
    }

    fun onAction(action: RepositoryBranchesAction) {
        when (action) {
            RepositoryBranchesAction.Refresh -> load(reset = true)
            RepositoryBranchesAction.Retry -> load(reset = _state.value.items.isEmpty())
            RepositoryBranchesAction.LoadMore -> load(reset = false)
            is RepositoryBranchesAction.Search -> _state.update { it.copy(query = action.query) }
            RepositoryBranchesAction.DismissWriteOutcome -> _state.update { it.copy(writeOutcome = null) }
            is RepositoryBranchesAction.CreateBranch -> createBranch(action.branch, action.sourceRef)
            is RepositoryBranchesAction.DeleteBranch -> deleteBranch(action.branch)
            is RepositoryBranchesAction.RenameBranch -> renameBranch(action.from, action.to)
        }
    }

    private fun createBranch(branch: String, sourceRef: String) {
        val target = branch.trim()
        val source = sourceRef.trim()
        val write = RepositoryBranchWrite.Create(target, source)
        if (!start(write)) return
        if (!_state.value.canWrite) {
            finish(write, RepositoryWriteFailure.Forbidden)
            return
        }
        if (!GithubGitRef.isValidBranchName(target) || !GithubGitRef.isValidBranchName(source)) {
            finish(write, RepositoryWriteFailure.InvalidInput)
            return
        }
        submit(write) {
            // The source branch may not be in the loaded pages and a cached SHA is not
            // the current tip, so resolve it as late as the commit itself.
            repository.resolveRef(owner, name, source).fold(
                onSuccess = { repository.createBranch(owner, name, target, it) },
                onFailure = { Result.failure(it) }
            )
        }
    }

    private fun deleteBranch(branch: String) {
        val target = branch.trim()
        val write = RepositoryBranchWrite.Delete(target)
        if (!start(write)) return
        if (!_state.value.canWrite) {
            finish(write, RepositoryWriteFailure.Forbidden)
            return
        }
        if (!GithubGitRef.isValidBranchName(target)) {
            finish(write, RepositoryWriteFailure.InvalidInput)
            return
        }
        submit(write) { repository.deleteBranch(owner, name, target) }
    }

    private fun renameBranch(from: String, to: String) {
        val target = to.trim()
        val write = RepositoryBranchWrite.Rename(from, target)
        if (!start(write)) return
        if (!_state.value.canWrite) {
            finish(write, RepositoryWriteFailure.Forbidden)
            return
        }
        if (!GithubGitRef.isValidBranchName(from) || !GithubGitRef.isValidBranchName(target) || from == target) {
            finish(write, RepositoryWriteFailure.InvalidInput)
            return
        }
        submit(write) { repository.renameBranch(owner, name, from, target) }
    }

    /** Only one write at a time; a tap while one is running must not race it. */
    private fun start(write: RepositoryBranchWrite): Boolean {
        if (_state.value.pendingWrite != null) return false
        _state.update { it.copy(pendingWrite = write, writeOutcome = null) }
        return true
    }

    private fun finish(write: RepositoryBranchWrite, failure: RepositoryWriteFailure? = null) {
        val outcome = if (failure == null) {
            RepositoryBranchWriteOutcome.Succeeded(write)
        } else {
            RepositoryBranchWriteOutcome.Failed(write, failure)
        }
        _state.update { it.copy(pendingWrite = null, writeOutcome = outcome) }
    }

    private fun submit(write: RepositoryBranchWrite, operation: suspend () -> Result<*>) {
        writeJob?.cancel()
        writeJob = viewModelScope.launch {
            operation().fold(
                onSuccess = {
                    finish(write)
                    load(reset = true)
                },
                onFailure = { finish(write, githubWriteFailure(it)) }
            )
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.branches(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset, GithubBranch::name),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val defaultBranch: String,
        private val repository: GithubRepositoryContentsRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryBranchesViewModel::class.java))
            return RepositoryBranchesViewModel(owner, name, defaultBranch, repository, detailsRepository) as T
        }
    }
}
