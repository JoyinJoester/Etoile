package takagi.ru.monica.github.feature.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

enum class ExploreTopic(private val template: String) {
    // "为你推荐"跟踪近 30 天冒出的新星仓库，而不是全时段高星老项目。
    FOR_YOU("created:>{date} stars:>50 sort:stars-desc"),
    KOTLIN("language:kotlin stars:>1000"),
    ANDROID("android stars:>1000"),
    COMPOSE("compose language:kotlin");

    fun query(today: java.time.LocalDate = java.time.LocalDate.now()): String =
        template.replace("{date}", today.minusDays(30).toString())
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
    private val globalSearch: GithubGlobalSearchRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState(
        query = savedStateHandle["query"] ?: "",
        searchKind = ExploreSearchKind.entries.firstOrNull { it.name == savedStateHandle.get<String>("searchKind") }
            ?: ExploreSearchKind.REPOSITORIES,
        selectedTopic = ExploreTopic.entries.firstOrNull { it.name == savedStateHandle.get<String>("topic") }
            ?: ExploreTopic.FOR_YOU
    ))
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var sessionObserved = false
    private var accountId: Long? = null
    private var accountLogin: String? = null
    private var searchesEnabled = true

    init {
        if (_state.value.query.isNotBlank() || _state.value.searchKind == ExploreSearchKind.REPOSITORIES) {
            searchNow(currentSearchQuery())
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val next = (session as? GithubSession.SignedIn)?.account
        val canSearch = session is GithubSession.SignedIn || session == GithubSession.SignedOut
        if (sessionObserved && accountId == next?.id &&
            accountLogin.equals(next?.login, ignoreCase = true) && searchesEnabled == canSearch
        ) return
        sessionObserved = true
        accountId = next?.id
        accountLogin = next?.login
        searchesEnabled = canSearch
        invalidateSearch()
        _state.update {
            // Keep the user's search intent, but never retain results fetched with another account's token.
            it.copy(
                repositories = emptyList(), users = emptyList(), code = emptyList(), conversations = emptyList(),
                nextPage = null, isLoading = false, isLoadingMore = false, error = false
            )
        }
        if (canSearch && (_state.value.query.isNotBlank() || _state.value.searchKind == ExploreSearchKind.REPOSITORIES)) {
            searchNow(currentSearchQuery())
        }
    }

    fun onAction(action: ExploreAction) {
        when (action) {
            is ExploreAction.QueryChanged -> updateQuery(action.query)
            is ExploreAction.SearchKindSelected -> selectSearchKind(action.kind)
            is ExploreAction.TopicSelected -> selectTopic(action.topic)
            ExploreAction.Retry -> retry()
            ExploreAction.LoadMore -> loadMore()
        }
        savedStateHandle["query"] = _state.value.query
        savedStateHandle["searchKind"] = _state.value.searchKind.name
        savedStateHandle["topic"] = _state.value.selectedTopic.name
    }

    private fun updateQuery(query: String) {
        invalidateSearch()
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
                searchNow(ExploreTopic.FOR_YOU.query())
            } else {
                _state.update { it.copy(isLoading = false) }
            }
            return
        }
        requestDebounced(query.trim())
    }

    private fun selectTopic(topic: ExploreTopic) {
        invalidateSearch()
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
        searchNow(topic.query())
    }

    private fun selectSearchKind(kind: ExploreSearchKind) {
        invalidateSearch()
        _state.update {
            it.copy(
                searchKind = kind,
                selectedTopic = ExploreTopic.FOR_YOU,
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                conversations = emptyList(),
                nextPage = null,
                isLoading = kind == ExploreSearchKind.REPOSITORIES || it.query.isNotBlank(),
                isLoadingMore = false,
                error = false
            )
        }
        if (kind == ExploreSearchKind.REPOSITORIES) {
            if (_state.value.query.isBlank()) searchNow(ExploreTopic.FOR_YOU.query())
            else requestDebounced(_state.value.query.trim())
        } else if (_state.value.query.isNotBlank()) {
            requestDebounced(_state.value.query.trim())
        }
    }

    private fun searchNow(query: String) {
        invalidateSearch()
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
        launchRequest(query, page = 1, reset = true)
    }

    private fun retry() {
        val state = _state.value
        if (state.query.isBlank() && state.searchKind != ExploreSearchKind.REPOSITORIES) return
        val reset = state.itemCount == 0
        val page = if (reset) 1 else state.nextPage ?: 1
        invalidateSearch()
        _state.update {
            it.copy(isLoading = reset, isLoadingMore = !reset, error = false)
        }
        launchRequest(currentSearchQuery(), page, reset)
    }

    private fun loadMore() {
        val state = _state.value
        if (!state.canLoadMore) return
        val page = state.nextPage ?: return
        invalidateSearch()
        _state.update { it.copy(isLoadingMore = true, error = false) }
        launchRequest(currentSearchQuery(), page, reset = false)
    }

    private suspend fun request(
        query: String,
        kind: ExploreSearchKind,
        page: Int,
        reset: Boolean,
        generation: Long
    ) {
        try {
            val result: Result<SearchPayload> = when (kind) {
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
            if (generation != searchGeneration || !currentCoroutineContext().isActive) return
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
        _state.value.selectedTopic.query()
    }

    private fun requestDebounced(query: String) {
        launchRequest(query, page = 1, reset = true, debounced = true)
    }

    private fun invalidateSearch() {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
    }

    private fun launchRequest(query: String, page: Int, reset: Boolean, debounced: Boolean = false) {
        if (!searchesEnabled) {
            _state.update { it.copy(isLoading = false, isLoadingMore = false) }
            return
        }
        val generation = searchGeneration
        val kind = _state.value.searchKind
        searchJob = viewModelScope.launch {
            if (debounced) delay(350)
            request(query, kind, page, reset, generation)
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
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ExploreViewModel::class.java))
            return ExploreViewModel(repository, globalSearch, extras.createSavedStateHandle()) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ExploreViewModel::class.java))
            return ExploreViewModel(repository, globalSearch) as T
        }
    }
}
