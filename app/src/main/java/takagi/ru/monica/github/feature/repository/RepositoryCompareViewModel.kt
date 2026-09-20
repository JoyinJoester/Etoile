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
import takagi.ru.monica.github.domain.GithubBranchComparison
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestsRepository

sealed interface RepositoryCompareAction {
    data object Retry : RepositoryCompareAction
}

@Immutable
data class RepositoryCompareUiState(
    val owner: String,
    val name: String,
    val base: String,
    val head: String,
    val comparison: GithubBranchComparison? = null,
    val isLoading: Boolean = true,
    // A comparison is a read, but GitHub refuses it with the same statuses the write
    // paths already classify, so naming those reasons does not need a second taxonomy.
    val failure: RepositoryWriteFailure? = null
) {
    val fullName: String get() = "$owner/$name"
    val refRange: String get() = "$base...$head"
    val files: List<GithubPullRequestFile> get() = comparison?.files ?: emptyList()
}

class RepositoryCompareViewModel(
    private val owner: String,
    private val name: String,
    private val base: String,
    private val head: String,
    private val repository: GithubPullRequestsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryCompareUiState(owner, name, base, head))
    val state: StateFlow<RepositoryCompareUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: RepositoryCompareAction) {
        when (action) {
            RepositoryCompareAction.Retry -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, failure = null) }
        loadJob = viewModelScope.launch {
            // The create-PR preview asks for one commit, while this page wants the file
            // list, so it requests a full page: GitHub still caps a comparison at 300 files.
            repository.compare(owner, name, base, head, perPage = FILES_PER_PAGE).fold(
                onSuccess = { comparison ->
                    _state.update { it.copy(comparison = comparison, isLoading = false, failure = null) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, failure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    companion object {
        private const val FILES_PER_PAGE = 100
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val base: String,
        private val head: String,
        private val repository: GithubPullRequestsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryCompareViewModel::class.java))
            return RepositoryCompareViewModel(owner, name, base, head, repository) as T
        }
    }
}
