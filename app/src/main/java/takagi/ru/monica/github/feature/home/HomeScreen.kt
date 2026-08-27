package takagi.ru.monica.github.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPreferenceGroup
import takagi.ru.monica.github.component.GithubPreferenceGroupDivider
import takagi.ru.monica.github.component.GithubPreferenceRow
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarCategory
import takagi.ru.monica.github.feature.mywork.MyConversationsKind
import takagi.ru.monica.github.feature.starred.StarredUiState

@Composable
fun HomeScreen(
    session: GithubSession,
    starredState: StarredUiState,
    onSignIn: () -> Unit,
    onRetrySession: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenMyConversations: (MyConversationsKind) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val comingSoonNotice = stringResource(R.string.github_home_coming_soon_detail)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
    ) {
        item(key = "intro") {
            GithubScreenIntro(
                subtitle = stringResource(R.string.github_home_subtitle)
            )
        }
        item(key = "my-work") {
            when (session) {
                GithubSession.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                GithubSession.SignedOut -> GithubAuthPromptCard(
                    title = stringResource(R.string.github_sign_in),
                    description = stringResource(R.string.github_home_sign_in_description),
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

                is GithubSession.SignedIn -> MyWorkSection(
                    account = session.account,
                    onOpenStarred = onOpenStarred,
                    onOpenRepositories = onOpenRepositories,
                    onOpenOrganizations = onOpenOrganizations,
                    onOpenMyConversations = onOpenMyConversations,
                    onComingSoon = { Toast.makeText(context, comingSoonNotice, Toast.LENGTH_SHORT).show() }
                )
            }
        }
        if (session is GithubSession.SignedIn) {
            item(key = "favorites") {
                FavoritesSection(
                    state = starredState,
                    onOpenStarred = onOpenStarred,
                    onOpenRepository = onOpenRepository
                )
            }
        }
    }
}

@Composable
private fun MyWorkSection(
    account: GithubAccount,
    onOpenStarred: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenMyConversations: (MyConversationsKind) -> Unit,
    onComingSoon: () -> Unit
) {
    GithubSectionHeader(
        title = stringResource(R.string.github_home_my_work),
        compact = true
    )
    GithubPreferenceGroup {
        GithubPreferenceRow(
            Icons.Default.Star,
            stringResource(R.string.github_starred),
            stringResource(R.string.github_manage),
            onClick = onOpenStarred
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
            Icons.Default.Public,
            stringResource(R.string.github_organizations),
            stringResource(R.string.github_open),
            onClick = onOpenOrganizations
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.RadioButtonChecked,
            stringResource(R.string.github_my_issues),
            stringResource(R.string.github_open),
            onClick = { onOpenMyConversations(MyConversationsKind.ISSUES) }
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.AutoMirrored.Filled.CallSplit,
            stringResource(R.string.github_my_pull_requests),
            stringResource(R.string.github_open),
            onClick = { onOpenMyConversations(MyConversationsKind.PULL_REQUESTS) }
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.Forum,
            stringResource(R.string.github_discussions),
            stringResource(R.string.github_home_coming_soon),
            onClick = onComingSoon
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.Dashboard,
            stringResource(R.string.github_projects),
            stringResource(R.string.github_home_coming_soon),
            onClick = onComingSoon
        )
    }
}

@Composable
private fun FavoritesSection(
    state: StarredUiState,
    onOpenStarred: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit
) {
    GithubSectionHeader(
        title = stringResource(R.string.github_home_favorites),
        compact = true
    )
    val categorized = state.repositories.filter { it.category != GithubStarCategory.ALL }
    when {
        state.isLoading && categorized.isEmpty() ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        categorized.isEmpty() -> GithubMessageState(
            title = stringResource(R.string.github_home_favorites_empty),
            actionLabel = stringResource(R.string.github_home_favorites_empty_action),
            onAction = onOpenStarred
        )

        else -> {
            val descriptionFallback = stringResource(R.string.github_no_description)
            val languageFallback = stringResource(R.string.github_unknown_language)
            val updatedFallback = stringResource(R.string.github_updated_recently)
            GithubStarCategory.entries
                .filter { it != GithubStarCategory.ALL }
                .forEach { category ->
                    val items = categorized.filter { it.category == category }
                    if (items.isNotEmpty()) {
                        Text(
                            text = categoryLabel(category),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = GithubExpressiveShapes.container,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                items.forEach { item ->
                                    GithubRepositoryRow(
                                        repository = item.repository,
                                        descriptionFallback = descriptionFallback,
                                        languageFallback = languageFallback,
                                        updatedFallback = updatedFallback,
                                        onClick = { onOpenRepository(item.repository) }
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun categoryLabel(category: GithubStarCategory): String = when (category) {
    GithubStarCategory.ALL -> stringResource(R.string.github_star_category_uncategorized)
    GithubStarCategory.ANDROID -> stringResource(R.string.github_star_category_android)
    GithubStarCategory.KOTLIN -> stringResource(R.string.github_star_category_kotlin)
    GithubStarCategory.TOOLS -> stringResource(R.string.github_star_category_tools)
}
