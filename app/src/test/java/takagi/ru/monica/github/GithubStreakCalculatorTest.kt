package takagi.ru.monica.github

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.github.domain.GithubContributionDay
import takagi.ru.monica.github.feature.profile.GithubStreakCalculator
import java.time.LocalDate

class GithubStreakCalculatorTest {

    private fun day(date: String, count: Int) =
        GithubContributionDay(date, count, if (count > 0) 1 else 0)

    @Test
    fun `longest streak counts consecutive active days`() {
        val days = listOf(
            day("2026-01-01", 1),
            day("2026-01-02", 3),
            day("2026-01-03", 0),
            day("2026-01-04", 2),
            day("2026-01-05", 5)
        )
        assertEquals(2, GithubStreakCalculator.longestStreak(days))
    }

    @Test
    fun `longest streak handles unordered input`() {
        val days = listOf(
            day("2026-01-05", 5),
            day("2026-01-04", 2),
            day("2026-01-02", 3)
        )
        assertEquals(2, GithubStreakCalculator.longestStreak(days))
    }

    @Test
    fun `empty calendar yields zero`() {
        assertEquals(0, GithubStreakCalculator.longestStreak(emptyList()))
        assertEquals(0, GithubStreakCalculator.currentStreak(emptyList()))
    }

    @Test
    fun `current streak ignores future days and counts back from today`() {
        val days = listOf(
            day("2026-01-01", 0),
            day("2026-01-02", 1),
            day("2026-01-03", 4),
            day("2026-01-05", 9)
        )
        assertEquals(2, GithubStreakCalculator.currentStreak(days, today = LocalDate.parse("2026-01-03")))
    }

    @Test
    fun `current streak stops at first inactive day`() {
        val days = listOf(
            day("2026-01-01", 2),
            day("2026-01-02", 0),
            day("2026-01-03", 3)
        )
        assertEquals(1, GithubStreakCalculator.currentStreak(days, today = LocalDate.parse("2026-01-03")))
    }
}
