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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubProfileAchievement
import takagi.ru.monica.github.domain.GithubProfileAchievementState
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import java.time.LocalDate

@Immutable
data class ProfileUiState(
    val login: String? = null,
    val calendar: GithubContributionCalendar? = null,
    val isLoadingCalendar: Boolean = false,
    val calendarError: Boolean = false,
    val readme: String? = null,
    val isLoadingReadme: Boolean = false,
    val readmeLoaded: Boolean = false,
    val longestStreak: Int = 0,
    val currentStreak: Int = 0,
    val isRefreshing: Boolean = false,
    val achievements: List<GithubProfileAchievementState> = emptyList()
) {
    val hasProfileContent: Boolean
        get() = calendar != null || (readmeLoaded && readme != null)
}

sealed interface ProfileAction {
    data object Retry : ProfileAction
    data object Refresh : ProfileAction
}

/**
 * "我"标签页的内容型 ViewModel：贡献热力图(GitHub GraphQL)、个人主页
 * README(<login>/<login> 仓库)与本地推导的成就。账户基础信息仍来自会话。
 */
class ProfileViewModel(
    private val contributionsRepository: GithubContributionsRepository,
    private val repositoryDetailsRepository: GithubRepositoryDetailsRepository,
    private val publicUserRepository: GithubPublicUserRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var account: GithubAccount? = null
    private var createdAt: LocalDate? = null
    private var loadedLogin: String? = null
    private var calendarJob: Job? = null
    private var readmeJob: Job? = null
    private var createdAtJob: Job? = null
    private var sessionGeneration = 0L
    private var calendarRequestGeneration = 0L
    private var readmeRequestGeneration = 0L
    private var createdAtRequestGeneration = 0L

    fun onSessionChanged(session: GithubSession) {
        val next = (session as? GithubSession.SignedIn)?.account
        if (next?.id == account?.id && next?.login.equals(loadedLogin, ignoreCase = true)) {
            account = next
            if (next != null) recomputeAchievements()
            return
        }
        sessionGeneration++
        calendarJob?.cancel()
        readmeJob?.cancel()
        createdAtJob?.cancel()
        account = next
        createdAt = null
        loadedLogin = next?.login
        _state.value = ProfileUiState(login = loadedLogin)
        if (next == null) return
        recomputeAchievements()
        fetchCreatedAt(next.login)
        loadCalendar(next.login)
        loadReadme(next.login)
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Retry -> loadedLogin?.let { login ->
                _state.update { it.copy(calendarError = false) }
                loadCalendar(login)
                if (!_state.value.readmeLoaded) loadReadme(login)
            }
            ProfileAction.Refresh -> loadedLogin?.let { login ->
                _state.update { it.copy(isRefreshing = true, calendarError = false) }
                loadCalendar(login, refreshing = true)
                loadReadme(login)
            }
        }
    }

    private fun fetchCreatedAt(login: String) {
        val generation = sessionGeneration
        val requestGeneration = ++createdAtRequestGeneration
        createdAtJob?.cancel()
        createdAtJob = viewModelScope.launch {
            val result = publicUserRepository.user(login)
            if (!isActive || generation != sessionGeneration || requestGeneration != createdAtRequestGeneration) return@launch
            result.onSuccess { user ->
                createdAt = user.createdAt?.parseIsoDate()
                recomputeAchievements()
            }
        }
    }

    private fun loadCalendar(login: String, refreshing: Boolean = false) {
        val generation = sessionGeneration
        val requestGeneration = ++calendarRequestGeneration
        calendarJob?.cancel()
        _state.update {
            it.copy(
                isLoadingCalendar = !refreshing,
                calendarError = false,
                isRefreshing = if (refreshing) true else it.isRefreshing
            )
        }
        calendarJob = viewModelScope.launch {
            val result = contributionsRepository.getContributionCalendar(login)
            if (!isActive || generation != sessionGeneration || requestGeneration != calendarRequestGeneration) return@launch
            result.fold(
                onSuccess = { calendar ->
                    val days = calendar.weeks.flatMap { it.days }.sortedBy { it.date }
                    _state.update {
                        it.copy(
                            calendar = calendar,
                            isLoadingCalendar = false,
                            calendarError = false,
                            isRefreshing = false,
                            longestStreak = GithubStreakCalculator.longestStreak(days),
                            currentStreak = GithubStreakCalculator.currentStreak(days)
                        )
                    }
                    recomputeAchievements()
                },
                onFailure = {
                    _state.update { it.copy(isLoadingCalendar = false, calendarError = true, isRefreshing = false) }
                }
            )
        }
    }

    private fun loadReadme(login: String) {
        val generation = sessionGeneration
        val requestGeneration = ++readmeRequestGeneration
        readmeJob?.cancel()
        _state.update { it.copy(isLoadingReadme = true, readmeLoaded = false) }
        readmeJob = viewModelScope.launch {
            val result = repositoryDetailsRepository.readme(login, login)
            if (!isActive || generation != sessionGeneration || requestGeneration != readmeRequestGeneration) return@launch
            result.fold(
                onSuccess = { readme ->
                    _state.update {
                        it.copy(
                            readme = readme?.takeIf(String::isNotBlank),
                            isLoadingReadme = false,
                            readmeLoaded = true
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingReadme = false, readmeLoaded = true) }
                }
            )
        }
    }

    private fun recomputeAchievements() {
        val current = account ?: return
        _state.update { state ->
            state.copy(
                achievements = GithubProfileAchievement.resolve(
                    publicRepos = current.publicRepositories,
                    followers = current.followers,
                    totalContributions = state.calendar?.totalContributions ?: 0,
                    longestStreak = state.longestStreak,
                    accountAgeYears = createdAt?.let {
                        java.time.temporal.ChronoUnit.YEARS.between(it, java.time.LocalDate.now())
                    }
                )
            )
        }
    }

    private fun String.parseIsoDate(): LocalDate? = runCatching {
        // GitHub 返回 ISO-8601 时间戳(如 2012-07-16T07:00:00Z)，取日期部分即可。
        LocalDate.parse(substringBefore('T'))
    }.getOrNull()

    class Factory(
        private val contributionsRepository: GithubContributionsRepository,
        private val repositoryDetailsRepository: GithubRepositoryDetailsRepository,
        private val publicUserRepository: GithubPublicUserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(
                contributionsRepository,
                repositoryDetailsRepository,
                publicUserRepository
            ) as T
        }
    }
}
