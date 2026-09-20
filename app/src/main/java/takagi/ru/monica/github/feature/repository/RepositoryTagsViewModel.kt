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
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubGitRef
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.mergeItems

sealed interface RepositoryTagWrite {
    data class Create(val tag: String, val sourceRef: String) : RepositoryTagWrite
    data class Delete(val tag: String) : RepositoryTagWrite
}

sealed interface RepositoryTagWriteOutcome {
    data class Succeeded(val write: RepositoryTagWrite) : RepositoryTagWriteOutcome
    data class Failed(
        val write: RepositoryTagWrite,
        val failure: RepositoryWriteFailure
    ) : RepositoryTagWriteOutcome
}

sealed interface RepositoryTagsAction {
    data object Refresh : RepositoryTagsAction
    data object Retry : RepositoryTagsAction
    data object LoadMore : RepositoryTagsAction
    data object DismissWriteOutcome : RepositoryTagsAction
    data class Search(val query: String) : RepositoryTagsAction
    data class CreateTag(val tag: String, val sourceRef: String) : RepositoryTagsAction
    data class DeleteTag(val tag: String) : RepositoryTagsAction
}

@Immutable
data class RepositoryTagsUiState(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val items: List<GithubTag> = emptyList(),
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val pendingWrite: RepositoryTagWrite? = null,
    val writeOutcome: RepositoryTagWriteOutcome? = null,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
) {
    val fullName: String get() = "$owner/$name"
    val filteredItems: List<GithubTag>
        get() = query.trim().takeIf(String::isNotBlank)?.let { normalized ->
            items.filter { it.name.contains(normalized, ignoreCase = true) }
        } ?: items
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore

    /** Creating and deleting tags both need push access on this repository. */
    val canWrite: Boolean get() = viewerLogin != null && viewerRole.canPush
}

class RepositoryTagsViewModel(
    private val owner: String,
    private val name: String,
    defaultBranch: String,
    private val repository: GithubRepositoryContentsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryTagsUiState(owner, name, defaultBranch))
    val state: StateFlow<RepositoryTagsUiState> = _state.asStateFlow()
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

    fun onAction(action: RepositoryTagsAction) {
        when (action) {
            RepositoryTagsAction.Refresh -> load(reset = true)
            RepositoryTagsAction.Retry -> load(reset = _state.value.items.isEmpty())
            RepositoryTagsAction.LoadMore -> load(reset = false)
            is RepositoryTagsAction.Search -> _state.update { it.copy(query = action.query) }
            RepositoryTagsAction.DismissWriteOutcome -> _state.update { it.copy(writeOutcome = null) }
            is RepositoryTagsAction.CreateTag -> createTag(action.tag, action.sourceRef)
            is RepositoryTagsAction.DeleteTag -> deleteTag(action.tag)
        }
    }

    private fun createTag(tag: String, sourceRef: String) {
        val target = tag.trim()
        val source = sourceRef.trim()
        val write = RepositoryTagWrite.Create(target, source)
        if (!start(write)) return
        if (!_state.value.canWrite) {
            finish(write, RepositoryWriteFailure.Forbidden)
            return
        }
        if (!GithubGitRef.isValidTagName(target) || !GithubGitRef.isValidBranchName(source)) {
            finish(write, RepositoryWriteFailure.InvalidInput)
            return
        }
        submit(write) {
            // The base commit moves while this screen sits open, so resolve it as late
            // as the ref itself instead of trusting a listed SHA.
            repository.resolveRef(owner, name, source).fold(
                onSuccess = { repository.createTag(owner, name, target, it) },
                onFailure = { Result.failure(it) }
            )
        }
    }

    private fun deleteTag(tag: String) {
        val target = tag.trim()
        val write = RepositoryTagWrite.Delete(target)
        if (!start(write)) return
        if (!_state.value.canWrite) {
            finish(write, RepositoryWriteFailure.Forbidden)
            return
        }
        if (!GithubGitRef.isValidTagName(target)) {
            finish(write, RepositoryWriteFailure.InvalidInput)
            return
        }
        submit(write) { repository.deleteTag(owner, name, target) }
    }

    /** Only one write at a time; a tap while one is running must not race it. */
    private fun start(write: RepositoryTagWrite): Boolean {
        if (_state.value.pendingWrite != null) return false
        _state.update { it.copy(pendingWrite = write, writeOutcome = null) }
        return true
    }

    private fun finish(write: RepositoryTagWrite, failure: RepositoryWriteFailure? = null) {
        val outcome = if (failure == null) {
            RepositoryTagWriteOutcome.Succeeded(write)
        } else {
            RepositoryTagWriteOutcome.Failed(write, failure)
        }
        _state.update { it.copy(pendingWrite = null, writeOutcome = outcome) }
    }

    private fun submit(write: RepositoryTagWrite, operation: suspend () -> Result<*>) {
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
            repository.tags(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset, GithubTag::name),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(isLoading = false, isLoadingMore = false, error = true)
                    }
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
            require(modelClass.isAssignableFrom(RepositoryTagsViewModel::class.java))
            return RepositoryTagsViewModel(owner, name, defaultBranch, repository, detailsRepository) as T
        }
    }
}
