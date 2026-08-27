package takagi.ru.monica.github.feature.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.mergeItems

enum class ExploreTopic(val query: String) {
    FOR_YOU("stars:>1000 sort:stars-desc"),
    KOTLIN("language:kotlin stars:>1000"),
    ANDROID("android stars:>1000"),
    COMPOSE("compose language:kotlin")
}

enum class ExploreSearchKind { REPOSITORIES, USERS, CODE, ISSUES, PULL_REQUESTS }

@Immutable
data class ExploreUiState(
    val query: String = "",
    val searchKind: ExploreSearchKind = ExploreSearchKind.REPOSITORIES,
    val selectedTopic: ExploreTopic = ExploreTopic.FOR_YOU,
    val repositories: List<GithubRepository> = emptyList(),
    val users: List<GithubUserSearchResult> = emptyList(),
    val code: List<GithubCodeSearchResult> = emptyList(),
    val conversations: List<GithubIssueSearchResult> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val isCurated: Boolean
        get() = searchKind == ExploreSearchKind.REPOSITORIES &&
            query.isBlank() && selectedTopic == ExploreTopic.FOR_YOU
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
    val itemCount: Int get() = repositories.size + users.size + code.size + conversations.size
}

sealed interface ExploreAction {
    data class QueryChanged(val query: String) : ExploreAction
    data class SearchKindSelected(val kind: ExploreSearchKind) : ExploreAction
    data class TopicSelected(val topic: ExploreTopic) : ExploreAction
    data object Retry : ExploreAction
    data object LoadMore : ExploreAction
}

class ExploreViewModel(
    private val repository: GithubRepositorySearchRepository,
    private val globalSearch: GithubGlobalSearchRepository? = null
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        searchNow(ExploreTopic.FOR_YOU.query)
    }

    fun onAction(action: ExploreAction) {
        when (action) {
            is ExploreAction.QueryChanged -> updateQuery(action.query)
            is ExploreAction.SearchKindSelected -> selectSearchKind(action.kind)
            is ExploreAction.TopicSelected -> selectTopic(action.topic)
            ExploreAction.Retry -> retry()
            ExploreAction.LoadMore -> loadMore()
        }
    }

    private fun updateQuery(query: String) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = query,
                searchKind = it.searchKind,
                selectedTopic = ExploreTopic.FOR_YOU,
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                conversations = emptyList(),
                nextPage = null,
                isLoading = true,
                isLoadingMore = false,
                error = false
            )
        }
        if (query.isBlank()) {
            if (_state.value.searchKind == ExploreSearchKind.REPOSITORIES) {
                searchNow(ExploreTopic.FOR_YOU.query)
            } else {
                _state.update { it.copy(isLoading = false) }
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            request(query.trim(), page = 1, reset = true)
        }
    }

    private fun selectTopic(topic: ExploreTopic) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                searchKind = ExploreSearchKind.REPOSITORIES,
                selectedTopic = topic,
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                conversations = emptyList(),
                nextPage = null,
                isLoading = true,
                isLoadingMore = false,
                error = false
            )
        }
        searchNow(topic.query)
    }

    private fun selectSearchKind(kind: ExploreSearchKind) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchKind = kind,
                selectedTopic = ExploreTopic.FOR_YOU,
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                conversations = emptyList(),
                nextPage = null,
                isLoading = kind != ExploreSearchKind.REPOSITORIES && it.query.isBlank().not(),
                isLoadingMore = false,
                error = false
            )
        }
        if (kind == ExploreSearchKind.REPOSITORIES) {
            if (_state.value.query.isBlank()) searchNow(ExploreTopic.FOR_YOU.query)
            else requestDebounced(_state.value.query.trim())
        } else if (_state.value.query.isNotBlank()) {
            requestDebounced(_state.value.query.trim())
        }
    }

    private fun searchNow(query: String) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                conversations = emptyList(),
                nextPage = null,
                isLoading = true,
                isLoadingMore = false,
                error = false
            )
        }
        searchJob = viewModelScope.launch { request(query, page = 1, reset = true) }
    }

    private fun retry() {
        val state = _state.value
        val reset = state.itemCount == 0
        val page = if (reset) 1 else state.nextPage ?: 1
        searchJob?.cancel()
        _state.update {
            it.copy(isLoading = reset, isLoadingMore = !reset, error = false)
        }
        searchJob = viewModelScope.launch { request(currentSearchQuery(), page, reset) }
    }

    private fun loadMore() {
        val state = _state.value
        if (!state.canLoadMore) return
        val page = state.nextPage ?: return
        searchJob?.cancel()
        _state.update { it.copy(isLoadingMore = true, error = false) }
        searchJob = viewModelScope.launch { request(currentSearchQuery(), page, reset = false) }
    }

    private suspend fun request(query: String, page: Int, reset: Boolean) {
        try {
            val result: Result<SearchPayload> = when (_state.value.searchKind) {
                ExploreSearchKind.REPOSITORIES -> repository.search(query, page).map { SearchPayload.Repositories(it) }
                ExploreSearchKind.USERS -> globalSearch?.users(query, page)?.map { SearchPayload.Users(it) }
                    ?: Result.failure(IllegalStateException("global search is unavailable"))
                ExploreSearchKind.CODE -> globalSearch?.code(query, page)?.map { SearchPayload.Code(it) }
                    ?: Result.failure(IllegalStateException("global search is unavailable"))
                ExploreSearchKind.ISSUES -> globalSearch?.issues(query, page)
                    ?.map { SearchPayload.Conversations(it) }
                    ?: Result.failure(IllegalStateException("global search is unavailable"))
                ExploreSearchKind.PULL_REQUESTS -> globalSearch?.pullRequests(query, page)
                    ?.map { SearchPayload.Conversations(it) }
                    ?: Result.failure(IllegalStateException("global search is unavailable"))
            }
            result.fold(
                onSuccess = { payload ->
                    _state.update { state ->
                        when (payload) {
                            is SearchPayload.Repositories -> state.copy(
                                repositories = payload.page.mergeItems(state.repositories, reset, GithubRepository::id),
                                users = emptyList(), code = emptyList(), conversations = emptyList(),
                                nextPage = payload.page.nextPage,
                                isLoading = false, isLoadingMore = false, error = false
                            )
                            is SearchPayload.Users -> state.copy(
                                repositories = emptyList(),
                                users = payload.page.mergeItems(state.users, reset, GithubUserSearchResult::id),
                                code = emptyList(), conversations = emptyList(), nextPage = payload.page.nextPage,
                                isLoading = false, isLoadingMore = false, error = false
                            )
                            is SearchPayload.Code -> state.copy(
                                repositories = emptyList(), users = emptyList(),
                                code = payload.page.mergeItems(state.code, reset, GithubCodeSearchResult::id),
                                conversations = emptyList(),
                                nextPage = payload.page.nextPage,
                                isLoading = false, isLoadingMore = false, error = false
                            )
                            is SearchPayload.Conversations -> state.copy(
                                repositories = emptyList(), users = emptyList(), code = emptyList(),
                                conversations = payload.page.mergeItems(
                                    state.conversations,
                                    reset,
                                    GithubIssueSearchResult::id
                                ),
                                nextPage = payload.page.nextPage,
                                isLoading = false, isLoadingMore = false, error = false
                            )
                        }
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            repositories = if (reset) emptyList() else it.repositories,
                            users = if (reset) emptyList() else it.users,
                            code = if (reset) emptyList() else it.code,
                            conversations = if (reset) emptyList() else it.conversations,
                            isLoading = false,
                            isLoadingMore = false,
                            error = true
                        )
                    }
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    private fun currentSearchQuery(): String = _state.value.query.trim().ifBlank {
        _state.value.selectedTopic.query
    }

    private fun requestDebounced(query: String) {
        searchJob = viewModelScope.launch {
            delay(350)
            request(query, page = 1, reset = true)
        }
    }

    private sealed interface SearchPayload {
        data class Repositories(val page: GithubPage<GithubRepository>) : SearchPayload
        data class Users(val page: GithubPage<GithubUserSearchResult>) : SearchPayload
        data class Code(val page: GithubPage<GithubCodeSearchResult>) : SearchPayload
        data class Conversations(val page: GithubPage<GithubIssueSearchResult>) : SearchPayload
    }

    class Factory(
        private val repository: GithubRepositorySearchRepository,
        private val globalSearch: GithubGlobalSearchRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ExploreViewModel::class.java))
            return ExploreViewModel(repository, globalSearch) as T
        }
    }
}
