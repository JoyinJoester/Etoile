package takagi.ru.monica.github.domain

/**
 * 贡献日历。日期为 ISO-8601 字符串(如 2026-09-02)以保持 domain 纯 Kotlin，
 * 日期运算由 UI/feature 层负责。
 */
data class GithubContributionDay(
    val date: String,
    val count: Int,
    val level: Int
)

data class GithubContributionCalendar(
    val weeks: List<GithubContributionWeek>,
    val totalContributions: Int
)

data class GithubContributionWeek(
    val days: List<GithubContributionDay>
)
