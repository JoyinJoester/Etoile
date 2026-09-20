package takagi.ru.monica.github.domain

interface GithubContributionsRepository {
    suspend fun getContributionCalendar(username: String): Result<GithubContributionCalendar>
}
