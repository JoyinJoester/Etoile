package takagi.ru.monica.github.feature.profile

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
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class PublicUserProfileUiState(
    val login: String,
    val user: GithubPublicUser? = null,
    val repositories: List<GithubRepository> = emptyList(),
    val nextPage: Int? = null,
    val isLoadingUser: Boolean = true,
    val isLoadingRepositories: Boolean = true,
    val isLoadingMore: Boolean = false,
    val userError: Boolean = false,
    val repositoriesError: Boolean = false,
    val isFollowing: Boolean? = null,
    val isLoadingFollowing: Boolean = false,
    val isUpdatingFollowing: Boolean = false,
    val followingError: Boolean = false
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoadingRepositories && !isLoadingMore
}

sealed interface PublicUserProfileAction {
    data object RetryUser : PublicUserProfileAction
    data object RetryRepositories : PublicUserProfileAction
    data object LoadMore : PublicUserProfileAction
    data object RetryFollowing : PublicUserProfileAction
    data object ToggleFollowing : PublicUserProfileAction
}

class PublicUserProfileViewModel(
    private val login: String,
    private val repository: GithubPublicUserRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PublicUserProfileUiState(login))
    val state: StateFlow<PublicUserProfileUiState> = _state.asStateFlow()
    private var userJob: Job? = null
    private var repositoriesJob: Job? = null
    private var followingJob: Job? = null
    private var sessionLogin: String? = null

    init {
        loadUser()
        loadRepositories(reset = true)
    }

    fun onAction(action: PublicUserProfileAction) {
        when (action) {
            PublicUserProfileAction.RetryUser -> loadUser()
            PublicUserProfileAction.RetryRepositories -> loadRepositories(reset = _state.value.repositories.isEmpty())
            PublicUserProfileAction.LoadMore -> if (_state.value.canLoadMore) loadRepositories(reset = false)
            PublicUserProfileAction.RetryFollowing -> loadFollowing()
            PublicUserProfileAction.ToggleFollowing -> toggleFollowing()
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login?.equals(sessionLogin, ignoreCase = true) == true) return
        sessionLogin = login
        followingJob?.cancel()
        if (login == null || login.equals(this.login, ignoreCase = true)) {
            _state.update {
                it.copy(
                    isFollowing = null,
                    isLoadingFollowing = false,
                    isUpdatingFollowing = false,
                    followingError = false
                )
            }
        } else {
            loadFollowing()
        }
    }

    private fun loadUser() {
        userJob?.cancel()
        _state.update { it.copy(isLoadingUser = true, userError = false) }
        userJob = viewModelScope.launch {
            repository.user(login).fold(
                onSuccess = { user -> _state.update { it.copy(user = user, isLoadingUser = false, userError = false) } },
                onFailure = { _state.update { it.copy(isLoadingUser = false, userError = true) } }
            )
        }
    }

    private fun loadRepositories(reset: Boolean) {
        val page = if (reset) 1 else _state.value.nextPage ?: return
        repositoriesJob?.cancel()
        _state.update {
            it.copy(
                repositories = if (reset) emptyList() else it.repositories,
                isLoadingRepositories = reset,
                isLoadingMore = !reset,
                repositoriesError = false
            )
        }
        repositoriesJob = viewModelScope.launch {
            repository.repositories(login, page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            repositories = result.mergeItems(state.repositories, reset, GithubRepository::id),
                            nextPage = result.nextPage,
                            isLoadingRepositories = false,
                            isLoadingMore = false,
                            repositoriesError = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingRepositories = false, isLoadingMore = false, repositoriesError = true) } }
            )
        }
    }

    private fun loadFollowing() {
        if (sessionLogin == null || sessionLogin?.equals(login, ignoreCase = true) == true) return
        followingJob?.cancel()
        _state.update { it.copy(isLoadingFollowing = true, followingError = false) }
        followingJob = viewModelScope.launch {
            repository.viewerFollows(login).fold(
                onSuccess = { following ->
                    _state.update {
                        it.copy(
                            isFollowing = following,
                            isLoadingFollowing = false,
                            followingError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingFollowing = false, followingError = true) }
                }
            )
        }
    }

    private fun toggleFollowing() {
        val current = _state.value.isFollowing ?: return
        if (
            sessionLogin == null ||
            sessionLogin?.equals(login, ignoreCase = true) == true ||
            _state.value.isUpdatingFollowing
        ) return
        val target = !current
        _state.update { it.copy(isUpdatingFollowing = true, followingError = false) }
        followingJob?.cancel()
        followingJob = viewModelScope.launch {
            repository.setFollowing(login, target).fold(
                onSuccess = { following ->
                    _state.update { state ->
                        val followerDelta = when {
                            following && !current -> 1
                            !following && current -> -1
                            else -> 0
                        }
                        val user = state.user
                        state.copy(
                            user = user?.copy(
                                followers = (user.followers + followerDelta).coerceAtLeast(0)
                            ),
                            isFollowing = following,
                            isUpdatingFollowing = false,
                            followingError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingFollowing = false, followingError = true) }
                }
            )
        }
    }

    class Factory(
        private val login: String,
        private val repository: GithubPublicUserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PublicUserProfileViewModel::class.java))
            return PublicUserProfileViewModel(login, repository) as T
        }
    }
}
