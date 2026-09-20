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
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure
import takagi.ru.monica.github.feature.repository.githubWriteFailure

@Immutable
data class PublicUserProfileUiState(
    val login: String,
    val user: GithubPublicUser? = null,
    val repositories: List<GithubRepository> = emptyList(),
    val calendar: GithubContributionCalendar? = null,
    val isLoadingCalendar: Boolean = true,
    val calendarError: Boolean = false,
    val readme: String? = null,
    val isLoadingReadme: Boolean = true,
    val nextPage: Int? = null,
    val isLoadingUser: Boolean = true,
    val isLoadingRepositories: Boolean = true,
    val isLoadingMore: Boolean = false,
    val userError: Boolean = false,
    val repositoriesError: Boolean = false,
    val isFollowing: Boolean? = null,
    val isLoadingFollowing: Boolean = false,
    val isUpdatingFollowing: Boolean = false,
    val followingError: Boolean = false,
    val followingAccessError: Boolean = false,
    val isBlocked: Boolean? = null,
    val isUpdatingBlocked: Boolean = false,
    val blockedFailure: RepositoryWriteFailure? = null
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoadingRepositories && !isLoadingMore
}

sealed interface PublicUserProfileAction {
    data object RetryUser : PublicUserProfileAction
    data object RetryRepositories : PublicUserProfileAction
    data object RetryCalendar : PublicUserProfileAction
    data object RetryReadme : PublicUserProfileAction
    data object LoadMore : PublicUserProfileAction
    data object RetryFollowing : PublicUserProfileAction
    data object ToggleFollowing : PublicUserProfileAction
    data object RetryBlocked : PublicUserProfileAction
    data object ToggleBlocked : PublicUserProfileAction
}

class PublicUserProfileViewModel(
    private val login: String,
    private val repository: GithubPublicUserRepository,
    private val contributionsRepository: GithubContributionsRepository? = null,
    private val repositoryDetailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null
) : ViewModel() {
    private val _state = MutableStateFlow(PublicUserProfileUiState(login))
    val state: StateFlow<PublicUserProfileUiState> = _state.asStateFlow()
    private var userJob: Job? = null
    private var repositoriesJob: Job? = null
    private var calendarJob: Job? = null
    private var readmeJob: Job? = null
    private var followingJob: Job? = null
    private var blockedJob: Job? = null
    private var blockWriteJob: Job? = null
    private var sessionLogin: String? = null

    init {
        loadUser()
        loadRepositories(reset = true)

    }

    fun onAction(action: PublicUserProfileAction) {
        when (action) {
            PublicUserProfileAction.RetryUser -> loadUser()
            PublicUserProfileAction.RetryRepositories -> loadRepositories(reset = _state.value.repositories.isEmpty())
            PublicUserProfileAction.RetryCalendar -> loadCalendar()
            PublicUserProfileAction.RetryReadme -> loadReadme()
            PublicUserProfileAction.LoadMore -> if (_state.value.canLoadMore) loadRepositories(reset = false)
            PublicUserProfileAction.RetryFollowing -> loadFollowing()
            PublicUserProfileAction.ToggleFollowing -> toggleFollowing()
            PublicUserProfileAction.RetryBlocked -> loadBlocked()
            PublicUserProfileAction.ToggleBlocked -> toggleBlocked()
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login?.equals(sessionLogin, ignoreCase = true) == true) return
        sessionLogin = login
        followingJob?.cancel()
        blockedJob?.cancel()
        blockWriteJob?.cancel()
        if (login == null || login.equals(this.login, ignoreCase = true)) {
            _state.update {
                it.copy(
                    isFollowing = null,
                    isLoadingFollowing = false,
                    isUpdatingFollowing = false,
                    followingError = false, followingAccessError = false,
                    isBlocked = null,
                    isUpdatingBlocked = false,
                    blockedFailure = null
                )
            }
        } else {
            loadFollowing()
            loadBlocked()
        }
    }

    private fun loadCalendar() {
        if (_state.value.user == null || _state.value.user?.isOrganization == true) return
        val contributions = contributionsRepository ?: return
        calendarJob?.cancel()
        _state.update { it.copy(isLoadingCalendar = true, calendarError = false) }
        calendarJob = viewModelScope.launch {
            contributions.getContributionCalendar(login).fold(
                onSuccess = { calendar ->
                    _state.update {
                        it.copy(calendar = calendar, isLoadingCalendar = false, calendarError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingCalendar = false, calendarError = true) }
                }
            )
        }
    }

    /** 个人主页 README(<login>/<login> 仓库);404 时静默隐藏。 */
    private fun loadReadme() {
        if (_state.value.user == null || _state.value.user?.isOrganization == true) return
        val details = repositoryDetailsRepository ?: return
        readmeJob?.cancel()
        _state.update { it.copy(isLoadingReadme = true) }
        readmeJob = viewModelScope.launch {
            details.readme(login, login).fold(
                onSuccess = { readme ->
                    _state.update {
                        it.copy(readme = readme?.takeIf(String::isNotBlank), isLoadingReadme = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(readme = null, isLoadingReadme = false) }
                }
            )
        }
    }

    private fun loadUser() {
        userJob?.cancel()
        _state.update { it.copy(isLoadingUser = true, userError = false) }
        userJob = viewModelScope.launch {
            repository.user(login).fold(
                onSuccess = { user ->
                    _state.update { it.copy(user = user, isLoadingUser = false, userError = false,
                        isLoadingCalendar = false, isLoadingReadme = false) }
                    loadCalendar()
                    loadReadme()
                    loadFollowing()
                    loadBlocked()
                },
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
        if (_state.value.user == null || _state.value.user?.isOrganization == true) return
        if (sessionLogin == null || sessionLogin?.equals(login, ignoreCase = true) == true) return
        followingJob?.cancel()
        _state.update { it.copy(isLoadingFollowing = true, followingError = false, followingAccessError = false) }
        followingJob = viewModelScope.launch {
            repository.viewerFollows(login).fold(
                onSuccess = { following ->
                    _state.update {
                        it.copy(
                            isFollowing = following,
                            isLoadingFollowing = false,
                            followingError = false, followingAccessError = false
                        )
                    }
                },
                onFailure = { itError ->
                    _state.update { it.copy(isLoadingFollowing = false, followingError = true, followingAccessError =
                        itError.isAccessRefusal) }
                }
            )
        }
    }

    private fun toggleFollowing() {
        if (_state.value.user == null || _state.value.user?.isOrganization == true) return
        val current = _state.value.isFollowing ?: return
        if (
            sessionLogin == null ||
            sessionLogin?.equals(login, ignoreCase = true) == true ||
            _state.value.isUpdatingFollowing
        ) return
        val target = !current
        _state.update { it.copy(isUpdatingFollowing = true, followingError = false, followingAccessError = false) }
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
                            followingError = false, followingAccessError = false
                        )
                    }
                },
                onFailure = { itError ->
                    _state.update { it.copy(isUpdatingFollowing = false, followingError = true, followingAccessError =
                        itError.isAccessRefusal) }
                }
            )
        }
    }

    private fun loadBlocked() {
        // Read the state for organizations too: a block created elsewhere still needs its way out,
        // while the offer to block a new account stays limited to user profiles in the screen.
        if (_state.value.user == null) return
        if (sessionLogin == null || sessionLogin?.equals(login, ignoreCase = true) == true) return
        blockedJob?.cancel()
        _state.update { it.copy(blockedFailure = null) }
        blockedJob = viewModelScope.launch {
            repository.viewerBlocks(login).fold(
                onSuccess = { blocked ->
                    _state.update {
                        it.copy(isBlocked = blocked, blockedFailure = null)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(blockedFailure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    private fun toggleBlocked() {
        val current = _state.value.isBlocked ?: return
        if (
            _state.value.user == null ||
            sessionLogin == null ||
            sessionLogin?.equals(login, ignoreCase = true) == true ||
            _state.value.isUpdatingBlocked
        ) return
        val target = !current
        if (target && _state.value.user?.isOrganization == true) return
        _state.update { it.copy(isUpdatingBlocked = true, blockedFailure = null) }
        blockedJob?.cancel()
        blockWriteJob?.cancel()
        blockWriteJob = viewModelScope.launch {
            repository.setBlocked(login, target).fold(
                onSuccess = {
                    _state.update { it.copy(isUpdatingBlocked = false) }
                    refreshViewerRelationships()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isUpdatingBlocked = false, blockedFailure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    /**
     * Blocking can end the follow relationship on GitHub's side, so both states are read back
     * instead of trusting the value just sent.
     */
    private fun refreshViewerRelationships() {
        loadBlocked()
        loadFollowing()
    }

    class Factory(
        private val login: String,
        private val repository: GithubPublicUserRepository,
        private val contributionsRepository: GithubContributionsRepository? = null,
        private val repositoryDetailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PublicUserProfileViewModel::class.java))
            return PublicUserProfileViewModel(login, repository, contributionsRepository, repositoryDetailsRepository) as T
        }
    }
}

/** GitHub answers a spent quota with the same 403 it uses for a real refusal. */
private val Throwable?.isAccessRefusal: Boolean
    get() = this is GithubApiException &&
        !rateLimited && (statusCode == 401 || statusCode == 403)
