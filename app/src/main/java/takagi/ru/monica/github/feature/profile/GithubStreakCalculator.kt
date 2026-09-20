package takagi.ru.monica.github.feature.profile

import takagi.ru.monica.github.domain.GithubContributionDay
import java.time.LocalDate

/** 贡献连击(连续有贡献的天数)计算。domain 保持纯 Kotlin，日期解析在此完成。 */
object GithubStreakCalculator {

    /** 输入任意顺序的贡献日，返回历史最长连续天数(按日历相邻计算)。 */
    fun longestStreak(days: List<GithubContributionDay>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        days.sortedBy { it.date }.forEach { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            run = when {
                day.count <= 0 -> 0
                date != null && previous != null && date == previous.plusDays(1) -> run + 1
                else -> 1
            }
            best = maxOf(best, run)
            if (date != null) previous = date
        }
        return best
    }

    /** 返回截至 today(含)仍在延续的连续天数；未来日期被忽略，断档即中断。 */
    fun currentStreak(days: List<GithubContributionDay>, today: LocalDate = LocalDate.now()): Int {
        var run = 0
        var previous: LocalDate? = null
        days.sortedByDescending { it.date }.forEach { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            if (date == null || date.isAfter(today)) return@forEach
            val adjacent = previous == null || date == previous.minusDays(1)
            if (day.count > 0 && adjacent) {
                run++
                previous = date
            } else {
                return run
            }
        }
        return run
    }
}
