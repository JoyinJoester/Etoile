package takagi.ru.monica.github.feature.home

import androidx.compose.foundation.layout.fillMaxSize

import takagi.ru.monica.github.navigation.GithubWebUrls
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import takagi.ru.monica.github.component.GithubCenteredProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubContributionHeatmap
import takagi.ru.monica.github.component.GithubSkeletonList
import takagi.ru.monica.github.component.GithubSkeletonRow
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
import takagi.ru.monica.github.feature.mywork.MyConversationsKind
import takagi.ru.monica.github.feature.starred.StarredUiState

@Composable
fun HomeScreen(
    session: GithubSession,
    starredState: StarredUiState,
    contributionsState: HomeContributionsState?,
    onSignIn: () -> Unit,
    onRetrySession: () -> Unit,
    onRetryContributions: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenMyConversations: (MyConversationsKind) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    BoxWithConstraints(modifier) {
        val expanded = maxWidth >= GithubAdaptiveLayout.detailWorkspaceWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)
        GithubAdaptiveGrid(
            modifier = Modifier.fillMaxSize(),
            minimumColumnWidth = 440.dp,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
        ) {
            githubFullSpanItem(key = "intro") {
                GithubScreenIntro(
                    subtitle = stringResource(R.string.github_home_subtitle)
                )
            }
            githubFullSpanItem(key = "my-work") {
                when (session) {
                    GithubSession.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GithubCenteredProgress()
                    }

                    GithubSession.SignedOut -> HomeSignedOutHero(
                        onSignIn = onSignIn,
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
                        onOpenExternal = onOpenExternal
                    )
                }
            }
            if (session is GithubSession.SignedIn) {
                item(key = "contributions") {
                    ContributionsSection(
                        state = contributionsState,
                        onRetry = onRetryContributions,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                item(key = "favorites") {
                    Column(Modifier.padding(top = if (expanded) 16.dp else 0.dp)) {
                        FavoritesSection(
                            state = starredState,
                            onOpenStarred = onOpenStarred,
                            onOpenRepository = onOpenRepository
                        )
                    }
                }
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
    onOpenExternal: (String) -> Unit
) {
    GithubSectionHeader(
        title = stringResource(R.string.github_home_my_work),
        compact = true
    )
    val material = LocalDesignStyle.current == DesignStyle.MATERIAL
    GithubPreferenceGroup {
        GithubPreferenceRow(
            Icons.Default.Star,
            stringResource(R.string.github_starred),
            if (material) "" else stringResource(R.string.github_manage),
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
            if (material) "" else stringResource(R.string.github_open),
            onClick = onOpenOrganizations
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.RadioButtonChecked,
            stringResource(R.string.github_my_issues),
            if (material) "" else stringResource(R.string.github_open),
            onClick = { onOpenMyConversations(MyConversationsKind.ISSUES) }
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.AutoMirrored.Filled.CallSplit,
            stringResource(R.string.github_my_pull_requests),
            if (material) "" else stringResource(R.string.github_open),
            onClick = { onOpenMyConversations(MyConversationsKind.PULL_REQUESTS) }
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.Forum,
            stringResource(R.string.github_discussions),
            if (material) "GitHub" else stringResource(R.string.github_open_on_github),
            onClick = { onOpenExternal(GithubWebUrls.discussions()) }
        )
        GithubPreferenceGroupDivider()
        GithubPreferenceRow(
            Icons.Default.Dashboard,
            stringResource(R.string.github_projects),
            if (material) "GitHub" else stringResource(R.string.github_open_on_github),
            onClick = { onOpenExternal(GithubWebUrls.userProjects(account.login)) }
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
    val labeled = state.repositories.filter { it.labels.isNotEmpty() }
    when {
        state.isLoading && labeled.isEmpty() ->
            GithubSkeletonList(row = GithubSkeletonRow.LIST, rowCount = 4)

        labeled.isEmpty() -> GithubMessageState(
            title = stringResource(R.string.github_home_favorites_empty),
            actionLabel = stringResource(R.string.github_home_favorites_empty_action),
            onAction = onOpenStarred
        )

        else -> {
            val descriptionFallback = stringResource(R.string.github_no_description)
            val languageFallback = stringResource(R.string.github_unknown_language)
            val updatedFallback = stringResource(R.string.github_updated_recently)
            state.labels.forEach { label ->
                val items = labeled.filter { item -> item.labels.any { it.id == label.id } }
                if (items.isNotEmpty()) {
                    Text(
                        text = label.name,
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
private fun HomeSignedOutHero(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.prominent,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(36.dp))
                Text(
                    stringResource(R.string.github_home_welcome_title),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    stringResource(R.string.github_home_welcome_body),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = GithubExpressiveShapes.container
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Text(stringResource(R.string.github_sign_in), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.github_home_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.github_home_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        listOf(
            Icons.Default.Folder to R.string.github_home_welcome_feature_repositories,
            Icons.AutoMirrored.Filled.CallSplit to R.string.github_home_welcome_feature_issues,
            Icons.Default.Public to R.string.github_home_welcome_feature_organizations,
            Icons.Default.Star to R.string.github_home_welcome_feature_starred
        ).forEach { (icon, labelRes) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Text(
                text = stringResource(R.string.github_sign_in),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ContributionsSection(
    state: HomeContributionsState?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
                    state == null || state.isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                    state.hasError -> GithubMessageState(
                        title = stringResource(R.string.github_profile_calendar_error),
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = onRetry
                    )
                    state.calendar != null -> GithubContributionHeatmap(
                        calendar = state.calendar
                    )
                    else -> Unit
                }
            }
        }
    }
}
