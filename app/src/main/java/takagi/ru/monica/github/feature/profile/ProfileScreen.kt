package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubContributionHeatmap
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubPreferenceGroup
import takagi.ru.monica.github.component.GithubPreferenceGroupDivider
import takagi.ru.monica.github.component.GithubPreferenceRow
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubProfileAchievement
import takagi.ru.monica.github.domain.GithubProfileAchievementState
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun ProfileScreen(
    session: GithubSession,
    profileState: ProfileUiState,
    savedAccountCount: Int,
    onProfileAction: (ProfileAction) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetrySession: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val signedIn = session is GithubSession.SignedIn
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
        ) {
            item(key = "intro") {
                GithubScreenIntro(
                    subtitle = stringResource(R.string.github_profile_subtitle)
                )
            }
            item(key = "account") {
                when (session) {
                    GithubSession.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    GithubSession.SignedOut -> GithubAuthPromptCard(
                        title = stringResource(R.string.github_sign_in),
                        description = stringResource(R.string.github_sign_in_description),
                        actionLabel = stringResource(R.string.github_sign_in),
                        icon = Icons.Default.Person,
                        onAction = onSignIn,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    is GithubSession.Error -> GithubMessageState(
                        title = stringResource(R.string.github_session_error),
                        actionLabel = stringResource(R.string.github_retry_session),
                        onAction = onRetrySession,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    is GithubSession.SignedIn -> {
                        val account = session.account
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val summary: @Composable () -> Unit = {
                                ProfileHeader(account)
                                ProfileMetrics(
                                    account = account,
                                    onOpenRepositories = onOpenRepositories,
                                    onOpenFollowers = onOpenFollowers,
                                    onOpenFollowing = onOpenFollowing
                                )
                                GithubSectionHeader(
                                    title = stringResource(R.string.github_profile_links),
                                    compact = true
                                )
                                GithubPreferenceGroup {
                                    GithubPreferenceRow(
                                        Icons.Default.ManageAccounts,
                                        stringResource(R.string.github_manage_accounts),
                                        stringResource(R.string.github_saved_accounts_count, savedAccountCount),
                                        onClick = onManageAccounts
                                    )
                                    GithubPreferenceGroupDivider()
                                    GithubPreferenceRow(
                                        Icons.Default.Folder,
                                        stringResource(R.string.github_repositories),
                                        account.publicRepositories.toString(),
                                        onClick = onOpenRepositories
                                    )
                                    GithubPreferenceGroupDivider()
                                    GithubPreferenceRow(
                                        Icons.Default.Star,
                                        stringResource(R.string.github_starred),
                                        stringResource(R.string.github_manage),
                                        onClick = onOpenStarred
                                    )
                                    GithubPreferenceGroupDivider()
                                    GithubPreferenceRow(
                                        Icons.Default.Public,
                                        stringResource(R.string.github_organizations),
                                        stringResource(R.string.github_open),
                                        onClick = onOpenOrganizations
                                    )
                                    GithubPreferenceGroupDivider()
                                    GithubPreferenceRow(
                                        Icons.Default.Block,
                                        stringResource(R.string.github_blocked_users),
                                        stringResource(R.string.github_open),
                                        onClick = onOpenBlockedUsers
                                    )
                                    GithubPreferenceGroupDivider()
                                    GithubPreferenceRow(
                                        Icons.AutoMirrored.Filled.Logout,
                                        stringResource(R.string.github_sign_out),
                                        account.login,
                                        onClick = onSignOut
                                    )
                                }
                            }
                            val activity: @Composable () -> Unit = {
                                ContributionSection(profileState = profileState)
                                AchievementSection(states = profileState.achievements)
                                ReadmeSection(profileState = profileState, onOpenExternal = onOpenExternal)
                            }
                            val readableWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
                            if (readableWidth >= GithubAdaptiveLayout.detailTwoPaneWidth) {
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Column(Modifier.weight(0.38f)) { summary() }
                                    Column(Modifier.weight(0.62f)) { activity() }
                                }
                            } else {
                                Column {
                                    summary()
                                    activity()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (signedIn) {
        GithubPullToRefreshBox(
            isRefreshing = profileState.isRefreshing,
            onRefresh = { onProfileAction(ProfileAction.Refresh) },
            enabled = true,
            modifier = modifier.fillMaxSize()
        ) {
            content(Modifier.fillMaxSize())
        }
    } else {
        content(modifier)
    }
}

@Composable
private fun ProfileHeader(account: GithubAccount) {
    if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GithubAvatar(login = account.login, avatarUrl = account.avatarUrl,
                        size = 56.dp, shape = GithubExpressiveShapes.compact)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name ?: account.login,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        GithubTechnicalLabel(text = "@${account.login}", modifier = Modifier.padding(top = 2.dp))
                    }
                }
                account.bio?.takeIf(String::isNotBlank)?.let { bio ->
                    Text(bio, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
            MaterialTheme.colorScheme.primaryContainer
        } else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GithubAvatar(
                login = account.login,
                avatarUrl = account.avatarUrl,
                size = 88.dp,
                shape = GithubExpressiveShapes.prominent
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = account.name ?: account.login,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            GithubTechnicalLabel(
                text = "@${account.login}",
                modifier = Modifier.padding(top = 2.dp)
            )
            account.bio?.takeIf(String::isNotBlank)?.let { bio ->
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileMetrics(
    account: GithubAccount,
    onOpenRepositories: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ProfileMetricLayout(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            metrics = listOf(
                { metricModifier ->
                    GithubMetric(
                        account.publicRepositories.toString(),
                        stringResource(R.string.github_repositories),
                        MaterialTheme.colorScheme.primary,
                        metricModifier,
                        onClick = onOpenRepositories,
                        compact = true
                    )
                },
                { metricModifier ->
                    GithubMetric(
                        account.followers.toString(),
                        stringResource(R.string.github_followers),
                        MaterialTheme.colorScheme.secondary,
                        metricModifier,
                        onClick = onOpenFollowers,
                        compact = true
                    )
                },
                { metricModifier ->
                    GithubMetric(
                        account.following.toString(),
                        stringResource(R.string.github_following),
                        MaterialTheme.colorScheme.tertiary,
                        metricModifier,
                        onClick = onOpenFollowing,
                        compact = true
                    )
                }
            )
        )
    }
}

@Composable
private fun ProfileMetricLayout(
    metrics: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { it(Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ContributionSection(profileState: ProfileUiState) {
    GithubSectionHeader(
        title = stringResource(R.string.github_profile_contributions_title),
        compact = true
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                profileState.calendar != null -> {
                    ProfileMetricLayout(
                        modifier = Modifier.fillMaxWidth(),
                        metrics = listOf(
                            { metricModifier ->
                                GithubMetric(
                                    profileState.calendar.totalContributions.toString(),
                                    stringResource(R.string.github_profile_last_year),
                                    MaterialTheme.colorScheme.primary,
                                    metricModifier,
                                    compact = true
                                )
                            },
                            { metricModifier ->
                                GithubMetric(
                                    profileState.longestStreak.toString(),
                                    stringResource(R.string.github_profile_longest_streak),
                                    MaterialTheme.colorScheme.secondary,
                                    metricModifier,
                                    compact = true
                                )
                            },
                            { metricModifier ->
                                GithubMetric(
                                    profileState.currentStreak.toString(),
                                    stringResource(R.string.github_profile_current_streak),
                                    MaterialTheme.colorScheme.tertiary,
                                    metricModifier,
                                    compact = true
                                )
                            }
                        )
                    )
                    GithubContributionHeatmap(
                        calendar = profileState.calendar,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                profileState.isLoadingCalendar -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
                profileState.calendarError -> Text(
                    text = stringResource(R.string.github_profile_calendar_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Box(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AchievementSection(states: List<GithubProfileAchievementState>) {
    if (states.isEmpty()) return
    GithubSectionHeader(
        title = stringResource(R.string.github_profile_achievements_title),
        compact = true
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = (maxWidth.value / (120f * fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            states.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { state ->
                        GithubAchievementTile(state = state, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
@Composable
private fun GithubAchievementTile(
    state: GithubProfileAchievementState,
    modifier: Modifier = Modifier
) {
    val alpha = if (state.unlocked) 1f else 0.35f
    Surface(
        modifier = modifier
            .heightIn(min = 112.dp)
            .alpha(alpha),
        shape = GithubExpressiveShapes.control,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = state.achievement.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(state.achievement.titleRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(state.achievement.descriptionRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun GithubProfileAchievement.icon(): ImageVector = when (this) {
    GithubProfileAchievement.FIRST_COMMIT -> Icons.Default.CheckCircle
    GithubProfileAchievement.HUNDRED_COMMITS -> Icons.Default.Star
    GithubProfileAchievement.THOUSAND_COMMITS -> Icons.Default.EmojiEvents
    GithubProfileAchievement.WEEK_STREAK -> Icons.Default.Whatshot
    GithubProfileAchievement.MONTH_STREAK -> Icons.Default.LocalFireDepartment
    GithubProfileAchievement.SEASON_STREAK -> Icons.Default.Bolt
    GithubProfileAchievement.SOCIAL_BUTTERFLY -> Icons.Default.Groups
    GithubProfileAchievement.REPO_BUILDER -> Icons.Default.Folder
    GithubProfileAchievement.OPEN_SOURCE_VETERAN -> Icons.Default.HistoryEdu
}

private fun GithubProfileAchievement.titleRes(): Int = when (this) {
    GithubProfileAchievement.FIRST_COMMIT -> R.string.github_achievement_first_commit
    GithubProfileAchievement.HUNDRED_COMMITS -> R.string.github_achievement_hundred
    GithubProfileAchievement.THOUSAND_COMMITS -> R.string.github_achievement_thousand
    GithubProfileAchievement.WEEK_STREAK -> R.string.github_achievement_week_streak
    GithubProfileAchievement.MONTH_STREAK -> R.string.github_achievement_month_streak
    GithubProfileAchievement.SEASON_STREAK -> R.string.github_achievement_season_streak
    GithubProfileAchievement.SOCIAL_BUTTERFLY -> R.string.github_achievement_social
    GithubProfileAchievement.REPO_BUILDER -> R.string.github_achievement_builder
    GithubProfileAchievement.OPEN_SOURCE_VETERAN -> R.string.github_achievement_veteran
}

private fun GithubProfileAchievement.descriptionRes(): Int = when (this) {
    GithubProfileAchievement.FIRST_COMMIT -> R.string.github_achievement_first_commit_desc
    GithubProfileAchievement.HUNDRED_COMMITS -> R.string.github_achievement_hundred_desc
    GithubProfileAchievement.THOUSAND_COMMITS -> R.string.github_achievement_thousand_desc
    GithubProfileAchievement.WEEK_STREAK -> R.string.github_achievement_week_streak_desc
    GithubProfileAchievement.MONTH_STREAK -> R.string.github_achievement_month_streak_desc
    GithubProfileAchievement.SEASON_STREAK -> R.string.github_achievement_season_streak_desc
    GithubProfileAchievement.SOCIAL_BUTTERFLY -> R.string.github_achievement_social_desc
    GithubProfileAchievement.REPO_BUILDER -> R.string.github_achievement_builder_desc
    GithubProfileAchievement.OPEN_SOURCE_VETERAN -> R.string.github_achievement_veteran_desc
}

@Composable
private fun ReadmeSection(
    profileState: ProfileUiState,
    onOpenExternal: (String) -> Unit
) {
    if (profileState.isLoadingReadme) {
        GithubSectionHeader(
            title = stringResource(R.string.github_profile_readme_title),
            compact = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
        return
    }
    val readme = profileState.readme ?: return
    GithubSectionHeader(
        title = stringResource(R.string.github_profile_readme_title),
        compact = true
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        MarkdownPreviewText(
            markdown = readme,
            onOpenExternalLink = onOpenExternal,
            modifier = Modifier.padding(16.dp)
        )
    }
}
