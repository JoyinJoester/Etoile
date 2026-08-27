package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubPreferenceGroup
import takagi.ru.monica.github.component.GithubPreferenceGroupDivider
import takagi.ru.monica.github.component.GithubPreferenceRow
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession

data class ProfileLinks(
    val organizations: String = "https://github.com/settings/organizations"
)

@Composable
fun ProfileScreen(
    settings: AppSettings,
    session: GithubSession,
    savedAccountCount: Int,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetrySession: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    links: ProfileLinks = ProfileLinks()
) {
    LazyColumn(
        modifier = modifier,
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
                    ProfileHeader(account)
                    ProfileMetrics(
                        account = account,
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
                            onClick = { onOpenUrl(links.organizations) }
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
            }
        }
        item(key = "preferences") {
            GithubSectionHeader(
                title = stringResource(R.string.github_preferences),
                compact = true
            )
            GithubPreferenceGroup {
                GithubPreferenceRow(
                    Icons.Default.Tune,
                    stringResource(R.string.github_appearance),
                    settings.colorScheme.name.replace('_', ' '),
                    onClick = onOpenSettings
                )
                GithubPreferenceGroupDivider()
                GithubPreferenceRow(
                    Icons.Default.Language,
                    stringResource(R.string.github_language),
                    settings.language.name.lowercase().replace('_', ' '),
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(account: GithubAccount) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GithubAvatar(
                login = account.login,
                avatarUrl = account.avatarUrl,
                size = 64.dp,
                shape = GithubExpressiveShapes.control
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name ?: account.login,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${account.login}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                account.bio?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMetrics(
    account: GithubAccount,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GithubMetric(
                account.publicRepositories.toString(),
                stringResource(R.string.github_repositories),
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
                compact = true
            )
            GithubMetric(
                account.followers.toString(),
                stringResource(R.string.github_followers),
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f),
                onClick = onOpenFollowers,
                compact = true
            )
            GithubMetric(
                account.following.toString(),
                stringResource(R.string.github_following),
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
                onClick = onOpenFollowing,
                compact = true
            )
        }
    }
}
