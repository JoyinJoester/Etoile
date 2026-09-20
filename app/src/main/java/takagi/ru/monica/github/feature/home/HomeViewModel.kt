package takagi.ru.monica.github.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubSession

class HomeViewModel(
    private val contributionsRepository: GithubContributionsRepository
) : ViewModel() {

    private val _contributionsState = MutableStateFlow(HomeContributionsState())
    val contributionsState: StateFlow<HomeContributionsState> = _contributionsState.asStateFlow()
    private var accountId: Long? = null
    private var activeLogin: String? = null
    private var sessionObserved = false
    private var loadJob: Job? = null
    private var requestGeneration = 0L

    fun onSessionChanged(session: GithubSession) {
        val next = (session as? GithubSession.SignedIn)?.account
        if (sessionObserved && accountId == next?.id && activeLogin.equals(next?.login, ignoreCase = true)) return
        sessionObserved = true
        requestGeneration++
        loadJob?.cancel()
        accountId = next?.id
        activeLogin = next?.login
        _contributionsState.value = HomeContributionsState(login = activeLogin)
        next?.let { loadContributions(it.login) }
    }

    fun loadContributions(username: String) {
        requestContributions(username, force = false)
    }

    fun retryLoadContributions(username: String) {
        requestContributions(username, force = true)
    }

    private fun requestContributions(username: String, force: Boolean) {
        val login = username.trim().takeIf(String::isNotEmpty) ?: return
        // A callback retained by the previous screen must not load another account after a switch or logout.
        if (sessionObserved && !activeLogin.equals(login, ignoreCase = true)) return
        val sameAccount = activeLogin.equals(login, ignoreCase = true)
        if (!force && sameAccount &&
            (_contributionsState.value.isLoading || _contributionsState.value.calendar != null)
        ) return

        val generation = ++requestGeneration
        loadJob?.cancel()
        activeLogin = login
        _contributionsState.value = HomeContributionsState(isLoading = true, login = login)
        loadJob = viewModelScope.launch {
            val result = contributionsRepository.getContributionCalendar(login)
            if (!isActive || generation != requestGeneration) return@launch
            result
                .onSuccess { calendar ->
                    _contributionsState.value = HomeContributionsState(calendar = calendar, login = login)
                }
                .onFailure {
                    _contributionsState.value = HomeContributionsState(hasError = true, login = login)
                }
        }
    }

    class Factory(
        private val contributionsRepository: GithubContributionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(contributionsRepository) as T
    }
}
