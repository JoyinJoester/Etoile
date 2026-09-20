package takagi.ru.monica.github.domain

/**
 * 个人主页成就。GitHub 没有公开的 achievements REST 端点，因此本地依据
 * 贡献日历与账户统计推导解锁状态，UI 负责映射标题与图标。
 * domain 保持纯 Kotlin：注册年限由 feature 层计算后传入。
 */
enum class GithubProfileAchievement {
    FIRST_COMMIT,
    HUNDRED_COMMITS,
    THOUSAND_COMMITS,
    WEEK_STREAK,
    MONTH_STREAK,
    SEASON_STREAK,
    SOCIAL_BUTTERFLY,
    REPO_BUILDER,
    OPEN_SOURCE_VETERAN;

    companion object {
        /** 返回与枚举顺序一致的解锁状态列表，由 ViewModel 与 UI 共同消费。 */
        fun resolve(
            publicRepos: Int,
            followers: Int,
            totalContributions: Int,
            longestStreak: Int,
            accountAgeYears: Long?
        ): List<GithubProfileAchievementState> = entries.map { achievement ->
            GithubProfileAchievementState(
                achievement = achievement,
                unlocked = when (achievement) {
                    FIRST_COMMIT -> totalContributions >= 1
                    HUNDRED_COMMITS -> totalContributions >= 100
                    THOUSAND_COMMITS -> totalContributions >= 1000
                    WEEK_STREAK -> longestStreak >= 7
                    MONTH_STREAK -> longestStreak >= 30
                    SEASON_STREAK -> longestStreak >= 90
                    SOCIAL_BUTTERFLY -> followers >= 50
                    REPO_BUILDER -> publicRepos >= 10
                    OPEN_SOURCE_VETERAN -> (accountAgeYears ?: 0L) >= 3
                }
            )
        }
    }
}

data class GithubProfileAchievementState(
    val achievement: GithubProfileAchievement,
    val unlocked: Boolean
)
