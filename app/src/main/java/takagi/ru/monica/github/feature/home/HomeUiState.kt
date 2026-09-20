package takagi.ru.monica.github.feature.home

import takagi.ru.monica.github.domain.GithubContributionCalendar

data class HomeContributionsState(
    val calendar: GithubContributionCalendar? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val login: String? = null
)

/** 首页扩展内容的状态聚合,便于在导航壳层一次性传递。 */
data class HomeUiState(
    val contributionsState: HomeContributionsState? = null
)
