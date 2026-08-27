package takagi.ru.monica.github.feature.releases

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
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class ReleasesUiState(
    val owner: String,
    val name: String,
    val items: List<GithubRelease> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface ReleasesAction {
    data object Retry : ReleasesAction
    data object LoadMore : ReleasesAction
}

class ReleasesViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubReleasesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReleasesUiState(owner = owner, name = name))
    val state: StateFlow<ReleasesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: ReleasesAction) {
        when (action) {
            ReleasesAction.Retry -> load(reset = _state.value.items.isEmpty())
            ReleasesAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                error = false,
                items = if (reset) emptyList() else it.items
            )
        }
        loadJob = viewModelScope.launch {
            repository.releases(owner, name, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubRelease::id),
                            nextPage = page.nextPage,
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
        private val repository: GithubReleasesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleasesViewModel::class.java))
            return ReleasesViewModel(owner, name, repository) as T
        }
    }
}

@Immutable
sealed interface ReleaseReference {
    data class Id(val value: Long) : ReleaseReference
    data class Tag(val value: String) : ReleaseReference
}

@Immutable
data class ReleaseDetailUiState(
    val owner: String,
    val name: String,
    val reference: ReleaseReference,
    val release: GithubRelease? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}

sealed interface ReleaseDetailAction {
    data object Retry : ReleaseDetailAction
}

class ReleaseDetailViewModel(
    private val owner: String,
    private val name: String,
    private val reference: ReleaseReference,
    private val repository: GithubReleasesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReleaseDetailUiState(owner, name, reference))
    val state: StateFlow<ReleaseDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: ReleaseDetailAction) {
        when (action) {
            ReleaseDetailAction.Retry -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = viewModelScope.launch {
            val result = when (val currentReference = reference) {
                is ReleaseReference.Id -> repository.release(owner, name, currentReference.value)
                is ReleaseReference.Tag -> repository.releaseByTag(owner, name, currentReference.value)
            }
            result.fold(
                onSuccess = { release ->
                    _state.update { it.copy(release = release, isLoading = false, error = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val reference: ReleaseReference,
        private val repository: GithubReleasesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleaseDetailViewModel::class.java))
            return ReleaseDetailViewModel(owner, name, reference, repository) as T
        }
    }
}
