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
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubFileWrite
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class RepositoryFilesUiState(
    val owner: String,
    val name: String,
    val ref: String,
    val path: String,
    val branches: GithubPage<GithubBranch> = GithubPage(emptyList(), null),
    val tags: GithubPage<GithubTag> = GithubPage(emptyList(), null),
    val items: List<GithubContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingBranches: Boolean = true,
    val isLoadingTags: Boolean = false,
    val error: Boolean = false,
    val branchesError: Boolean = false,
    val tagsError: Boolean = false,
    val tagsLoaded: Boolean = false,
    val pendingMutation: RepositoryFileMutation? = null,
    val mutationOutcome: RepositoryFileMutationOutcome? = null,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
) {
    val fullName: String get() = "$owner/$name"
    val canWrite: Boolean get() = viewerLogin != null && viewerRole.canPush
    /**
     * The Contents API commits to a named branch. While browsing a tag the listed
     * branches are loaded, so a ref missing from them cannot receive a commit.
     */
    val isOnBranch: Boolean
        get() = branches.items.isEmpty() || branches.items.any { it.name == ref }
}

sealed interface RepositoryFileMutation {
    val path: String
    val message: String

    data class Create(
        override val path: String,
        override val message: String,
        val content: String
    ) : RepositoryFileMutation

    data class Delete(
        override val path: String,
        override val message: String,
        val sha: String
    ) : RepositoryFileMutation
}

sealed interface RepositoryFileMutationOutcome {
    data class Succeeded(val commitSha: String) : RepositoryFileMutationOutcome
    data class Failed(
        val mutation: RepositoryFileMutation,
        val failure: RepositoryWriteFailure
    ) : RepositoryFileMutationOutcome
}

sealed interface RepositoryFilesAction {
    data object Refresh : RepositoryFilesAction
    data object Retry : RepositoryFilesAction
    data object RetryBranches : RepositoryFilesAction
    data object LoadTags : RepositoryFilesAction
    data object LoadMoreBranches : RepositoryFilesAction
    data object LoadMoreTags : RepositoryFilesAction
    data object DismissMutation : RepositoryFilesAction
    data class CreateFile(
        val path: String,
        val message: String,
        val content: String
    ) : RepositoryFilesAction

    data class DeleteFile(
        val path: String,
        val sha: String,
        val message: String
    ) : RepositoryFilesAction
}

class RepositoryFilesViewModel(
    private val owner: String,
    private val name: String,
    private val ref: String,
    private val path: String,
    private val repository: GithubRepositoryContentsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryFilesUiState(owner, name, ref, path))
    val state: StateFlow<RepositoryFilesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null
    private var branchesJob: Job? = null
    private var tagsJob: Job? = null
    private var mutationJob: Job? = null

    init {
        loadBranches()
        load()
    }

    fun onAction(action: RepositoryFilesAction) {
        when (action) {
            RepositoryFilesAction.Refresh -> load(refreshing = true)
            RepositoryFilesAction.Retry -> load()
            RepositoryFilesAction.RetryBranches -> loadBranches()
            RepositoryFilesAction.LoadTags -> if (!_state.value.tagsLoaded || _state.value.tagsError) loadTags()
            RepositoryFilesAction.LoadMoreBranches -> _state.value.branches.nextPage?.let { loadBranches(it, append = true) }
            RepositoryFilesAction.LoadMoreTags -> _state.value.tags.nextPage?.let { loadTags(it, append = true) }
            RepositoryFilesAction.DismissMutation -> _state.update { it.copy(mutationOutcome = null) }
            is RepositoryFilesAction.CreateFile -> submitMutation(
                RepositoryFileMutation.Create(action.path.trim(), action.message.trim(), action.content)
            )
            is RepositoryFilesAction.DeleteFile -> submitMutation(
                RepositoryFileMutation.Delete(action.path.trim(), action.message.trim(), action.sha)
            )
        }
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

    private fun submitMutation(mutation: RepositoryFileMutation) {
        if (_state.value.pendingMutation != null) return
        _state.update { it.copy(pendingMutation = mutation, mutationOutcome = null) }
        val fail = { reason: RepositoryWriteFailure ->
            _state.update { it.copy(pendingMutation = null, mutationOutcome = RepositoryFileMutationOutcome.Failed(mutation, reason)) }
        }
        if (!_state.value.canWrite) {
            fail(RepositoryWriteFailure.Forbidden)
            return
        }
        val change = GithubFileWrite.fromInput(
            path = mutation.path,
            branch = ref,
            message = mutation.message,
            content = (mutation as? RepositoryFileMutation.Create)?.content,
            expectedSha = (mutation as? RepositoryFileMutation.Delete)?.sha
        ).getOrElse {
            fail(RepositoryWriteFailure.InvalidInput)
            return
        }
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            repository.write(owner, name, change).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            pendingMutation = null,
                            mutationOutcome = RepositoryFileMutationOutcome.Succeeded(result.commitSha)
                        )
                    }
                    load(refreshing = true)
                },
                onFailure = { fail(githubWriteFailure(it)) }
            )
        }
    }

    private fun loadBranches(page: Int = 1, append: Boolean = false) {
        branchesJob?.cancel()
        _state.update { it.copy(isLoadingBranches = true, branchesError = false) }
        branchesJob = viewModelScope.launch {
            repository.branches(owner, name, page = page).fold(
                onSuccess = { branches ->
                    _state.update {
                        it.copy(
                            branches = GithubPage(
                                items = branches.mergeItems(
                                    existing = if (append) it.branches.items else emptyList(),
                                    reset = !append,
                                    keySelector = GithubBranch::name
                                ),
                                nextPage = branches.nextPage
                            ),
                            isLoadingBranches = false,
                            branchesError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingBranches = false, branchesError = true) }
                }
            )
        }
    }

    private fun loadTags(page: Int = 1, append: Boolean = false) {
        tagsJob?.cancel()
        _state.update { it.copy(isLoadingTags = true, tagsError = false, tagsLoaded = true) }
        tagsJob = viewModelScope.launch {
            repository.tags(owner, name, page = page).fold(
                onSuccess = { tags ->
                    _state.update {
                        it.copy(
                            tags = GithubPage(
                                items = tags.mergeItems(
                                    existing = if (append) it.tags.items else emptyList(),
                                    reset = !append,
                                    keySelector = GithubTag::name
                                ),
                                nextPage = tags.nextPage
                            ),
                            isLoadingTags = false,
                            tagsError = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingTags = false, tagsError = true) } }
            )
        }
    }

    private fun load(refreshing: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, error = false) }
        loadJob = viewModelScope.launch {
            repository.directory(owner, name, path, ref).fold(
                onSuccess = { items ->
                    _state.update {
                        it.copy(
                            items = items.sortedWith(
                                compareBy<GithubContentItem> { item -> item.type != GithubContentType.DIRECTORY }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { item -> item.name }
                            ),
                            isLoading = false,
                            isRefreshing = false,
                            error = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoading = false, isRefreshing = false, error = true) } }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val ref: String,
        private val path: String,
        private val repository: GithubRepositoryContentsRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryFilesViewModel::class.java))
            return RepositoryFilesViewModel(owner, name, ref, path, repository, detailsRepository) as T
        }
    }
}

@Immutable
data class RepositoryFileUiState(
    val owner: String,
    val name: String,
    val ref: String,
    val path: String,
    val content: GithubFileContent? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val editing: Boolean = false,
    val draftText: String? = null,
    val writing: Boolean = false,
    val writeFailure: RepositoryWriteFailure? = null,
    val writtenCommit: String? = null,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
) {
    val fullName: String get() = "$owner/$name"
    val fileName: String get() = path.substringAfterLast('/')
    val canWrite: Boolean get() = viewerLogin != null && viewerRole.canPush
}

sealed interface RepositoryFileAction {
    data object Retry : RepositoryFileAction
    data object StartEdit : RepositoryFileAction
    data class DraftChanged(val text: String) : RepositoryFileAction
    data object CancelEdit : RepositoryFileAction
    data object Save : RepositoryFileAction
    data object DismissSaved : RepositoryFileAction
}

class RepositoryFileViewModel(
    private val owner: String,
    private val name: String,
    private val ref: String,
    private val path: String,
    private val repository: GithubRepositoryContentsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryFileUiState(owner, name, ref, path))
    val state: StateFlow<RepositoryFileUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null

    init {
        load()
    }

    fun onAction(action: RepositoryFileAction) {
        when (action) {
            RepositoryFileAction.Retry -> load()
            RepositoryFileAction.DismissSaved -> _state.update { it.copy(writtenCommit = null) }
            RepositoryFileAction.StartEdit -> startEdit()
            is RepositoryFileAction.DraftChanged -> if (action.text.length <= 512 * 1024) _state.update { it.copy(draftText = action.text, writeFailure = null) }
            RepositoryFileAction.CancelEdit -> _state.update { it.copy(editing = false, draftText = null, writeFailure = null) }
            RepositoryFileAction.Save -> save()
        }
    }

    private fun startEdit() {
        if (!_state.value.canWrite) {
            _state.update { it.copy(writeFailure = RepositoryWriteFailure.Forbidden) }
            return
        }
        (_state.value.content as? GithubFileContent.Text)?.let { text ->
            _state.update { it.copy(editing = true, draftText = text.value, writeFailure = null, writtenCommit = null) }
        }
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

    private fun save() {
        val current = _state.value
        val text = current.draftText ?: return
        if (current.writing) return
        if (!current.canWrite) {
            _state.update { it.copy(writeFailure = RepositoryWriteFailure.Forbidden) }
            return
        }
        val original = current.content as? GithubFileContent.Text ?: return
        if (text == original.value) { _state.update { it.copy(editing = false) }; return }
        val change = GithubFileWrite.fromInput(current.path, current.ref, "Update ${current.fileName}", text, original.sha)
            .getOrElse {
                _state.update { it.copy(writeFailure = RepositoryWriteFailure.InvalidInput) }
                return
            }
        _state.update { it.copy(writing = true, writeFailure = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.write(owner, name, change).fold(onSuccess = { result ->
                _state.update {
                    it.copy(
                        content = GithubFileContent.Text(text, result.contentSha ?: original.sha),
                        editing = false,
                        draftText = null,
                        writing = false,
                        writtenCommit = result.commitSha
                    )
                }
            }, onFailure = { error -> _state.update { s -> s.copy(writing = false, writeFailure = githubWriteFailure(error)) } })
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = viewModelScope.launch {
            repository.file(owner, name, path, ref).fold(
                onSuccess = { content ->
                    _state.update { it.copy(content = content, isLoading = false, error = false) }
                },
                onFailure = { _state.update { it.copy(isLoading = false, error = true) } }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val ref: String,
        private val path: String,
        private val repository: GithubRepositoryContentsRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryFileViewModel::class.java))
            return RepositoryFileViewModel(owner, name, ref, path, repository, detailsRepository) as T
        }
    }
}
