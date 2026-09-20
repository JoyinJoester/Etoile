package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.github.component.GithubUserLink
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubRefreshButton
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryFeature
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun RepositoryDetailScreen(
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onBack: () -> Unit,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenBranches: (GithubRepositoryDetails) -> Unit,
    onOpenTags: (GithubRepositoryDetails) -> Unit,
    onOpenCollaborators: (GithubRepositoryDetails) -> Unit,
    onOpenWebhooks: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenActions: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenCommits: (GithubRepositoryDetails) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = state.details
    GithubDetailScaffold(
        title = state.name,
        subtitle = state.owner,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        actions = {
            val url = details?.repository?.htmlUrl ?: GithubWebUrls.repository(state.fullName)
            GithubRefreshButton(
                onClick = { onAction(RepositoryDetailAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoadingDetails
            )
            GithubOpenOnGithubButton(onClick = { onOpenExternal(url) })
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                details == null && state.isLoadingDetails -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                details == null && state.detailsError -> {
                    GithubMessageState(
                        title = stringResource(R.string.github_repository_load_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(RepositoryDetailAction.RetryDetails) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                details != null && maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth *
                    androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f) -> {
                    RepositoryDetailExpanded(
                        details = details,
                        state = state,
                        onAction = onAction,
                        onBrowseCode = onBrowseCode,
                        onOpenBranches = onOpenBranches,
                        onOpenTags = onOpenTags,
                        onOpenCollaborators = onOpenCollaborators,
                        onOpenWebhooks = onOpenWebhooks,
                        onOpenIssues = onOpenIssues,
                        onOpenPullRequests = onOpenPullRequests,
                        onOpenActions = onOpenActions,
                        onOpenReleases = onOpenReleases,
                        onOpenCommits = onOpenCommits,
                        isSignedIn = isSignedIn,
                        onSignIn = onSignIn,
                        onOpenRepository = onOpenRepository,
                        onOpenExternal = onOpenExternal
                    )
                }
                details != null -> {
                    RepositoryDetailCompact(
                        details = details,
                        state = state,
                        onAction = onAction,
                        onBrowseCode = onBrowseCode,
                        onOpenBranches = onOpenBranches,
                        onOpenTags = onOpenTags,
                        onOpenCollaborators = onOpenCollaborators,
                        onOpenWebhooks = onOpenWebhooks,
                        onOpenIssues = onOpenIssues,
                        onOpenPullRequests = onOpenPullRequests,
                        onOpenActions = onOpenActions,
                        onOpenReleases = onOpenReleases,
                        onOpenCommits = onOpenCommits,
                        isSignedIn = isSignedIn,
                        onSignIn = onSignIn,
                        onOpenRepository = onOpenRepository,
                        onOpenExternal = onOpenExternal
                    )
                }
            }

            if (state.isLoadingDetails && details != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun RepositoryDetailCompact(
    details: GithubRepositoryDetails,
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenBranches: (GithubRepositoryDetails) -> Unit,
    onOpenTags: (GithubRepositoryDetails) -> Unit,
    onOpenCollaborators: (GithubRepositoryDetails) -> Unit,
    onOpenWebhooks: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenActions: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenCommits: (GithubRepositoryDetails) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            RepositorySummary(
                details,
                onBrowseCode,
                onOpenBranches,
                onOpenTags,
                onOpenCollaborators,
                onOpenWebhooks,
                onOpenIssues,
                onOpenPullRequests,
                onOpenActions,
                onOpenReleases,
                onOpenCommits,
                state.canEditTopics,
                state.canAdminister,
                state.isUpdatingTopics,
                state.topicsFailure,
                onUpdateTopics = { topics -> onAction(RepositoryDetailAction.UpdateTopics(topics)) },
                isSignedIn,
                onSignIn,
                onOpenExternal
            )
        }
        item {
            RepositoryBranchProtection(
                state = state,
                onRetry = { onAction(RepositoryDetailAction.RetryBranchProtection) }
            )
        }
        item {
            RepositoryAdministration(
                details = details,
                state = state,
                onAction = onAction
            )
        }
        item {
            RepositoryActionControls(
                state = state,
                isSignedIn = isSignedIn,
                onAction = onAction,
                onSignIn = onSignIn,
                onOpenRepository = onOpenRepository
            )
        }
        item {
            RepositoryReadme(
                details = details,
                state = state,
                onRetry = { onAction(RepositoryDetailAction.RetryReadme) },
                onOpenExternal = onOpenExternal
            )
        }
    }
}

@Composable
private fun RepositoryDetailExpanded(
    details: GithubRepositoryDetails,
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenBranches: (GithubRepositoryDetails) -> Unit,
    onOpenTags: (GithubRepositoryDetails) -> Unit,
    onOpenCollaborators: (GithubRepositoryDetails) -> Unit,
    onOpenWebhooks: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenActions: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenCommits: (GithubRepositoryDetails) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(360.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                RepositorySummary(
                    details,
                    onBrowseCode,
                    onOpenBranches,
                    onOpenTags,
                    onOpenCollaborators,
                    onOpenWebhooks,
                    onOpenIssues,
                    onOpenPullRequests,
                    onOpenActions,
                    onOpenReleases,
                    onOpenCommits,
                    state.canEditTopics,
                    state.canAdminister,
                    state.isUpdatingTopics,
                    state.topicsFailure,
                    onUpdateTopics = { topics -> onAction(RepositoryDetailAction.UpdateTopics(topics)) },
                    isSignedIn = isSignedIn,
                    onSignIn = onSignIn,
                    onOpenExternal = onOpenExternal
                )
            }
            item {
                RepositoryActionControls(
                    state = state,
                    isSignedIn = isSignedIn,
                    onAction = onAction,
                    onSignIn = onSignIn,
                    onOpenRepository = onOpenRepository
                )
            }
            item {
                RepositoryBranchProtection(
                    state = state,
                    onRetry = { onAction(RepositoryDetailAction.RetryBranchProtection) }
                )
            }
            item {
                RepositoryAdministration(
                    details = details,
                    state = state,
                    onAction = onAction
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
        ) {
            item {
                RepositoryReadme(
                    details = details,
                    state = state,
                    onRetry = { onAction(RepositoryDetailAction.RetryReadme) },
                    onOpenExternal = onOpenExternal
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepositorySummary(
    details: GithubRepositoryDetails,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenBranches: (GithubRepositoryDetails) -> Unit,
    onOpenTags: (GithubRepositoryDetails) -> Unit,
    onOpenCollaborators: (GithubRepositoryDetails) -> Unit,
    onOpenWebhooks: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenActions: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenCommits: (GithubRepositoryDetails) -> Unit,
    canEditTopics: Boolean,
    canAdminister: Boolean,
    isUpdatingTopics: Boolean,
    topicsFailure: RepositoryWriteFailure?,
    onUpdateTopics: (List<String>) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val repository = details.repository
    // 头部信息直接铺在页面背景上（官方风格），不再包卡片
    Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.padding(13.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (repository.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    GithubUserLink(
                        login = details.ownerLogin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = repository.description ?: stringResource(R.string.github_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp)
            )

            if (details.isArchived || details.isFork || details.topics.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (details.isArchived) {
                        RepositoryTag(
                            text = stringResource(R.string.github_archived_repository),
                            icon = Icons.Default.Archive
                        )
                    }
                    if (details.isFork) {
                        RepositoryTag(
                            text = stringResource(R.string.github_fork_repository),
                            icon = Icons.AutoMirrored.Filled.CallSplit
                        )
                    }
                    details.topics.forEach { topic ->
                        RepositoryTag(text = topic)
                    }
                }
            }
            RepositoryTopicsEditor(
                topics = details.topics,
                isUpdating = isUpdatingTopics,
                failure = topicsFailure,
                enabled = canEditTopics,
                onUpdate = onUpdateTopics,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GithubInlineStat(
                    icon = Icons.Default.Star,
                    value = formatCount(repository.stars),
                    label = stringResource(R.string.github_stars)
                )
                GithubInlineStat(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    value = formatCount(details.forks),
                    label = stringResource(R.string.github_forks)
                )
            }

            Spacer(Modifier.height(14.dp))
            GithubMetadataRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(R.string.github_default_branch),
                value = details.defaultBranch
            )
            GithubMetadataRow(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.github_watchers),
                value = formatCount(details.watchers)
            )
            details.license?.let { license ->
                GithubMetadataRow(
                    icon = Icons.Default.Balance,
                    title = stringResource(R.string.github_license),
                    value = license
                )
            }
            repository.updatedAt?.let { updatedAt ->
                GithubMetadataRow(
                    icon = Icons.Default.Update,
                    title = stringResource(R.string.github_updated_recently),
                    value = githubRelativeTime(updatedAt)
                )
            }

            Button(
                onClick = { onBrowseCode(details) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = GithubExpressiveShapes.control
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_browse_code))
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
            GithubCompactNavRow(
                icon = Icons.Default.BugReport,
                iconContainer = GithubSectionTints.issues,
                label = stringResource(R.string.github_view_issues),
                count = formatCount(details.openIssues)
            ) { onOpenIssues(details) }
            GithubCompactNavRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                iconContainer = GithubSectionTints.pulls,
                label = stringResource(R.string.github_view_pull_requests)
            ) { onOpenPullRequests(details) }
            GithubCompactNavRow(
                icon = Icons.Default.Forum,
                iconContainer = GithubSectionTints.discussions,
                label = stringResource(R.string.github_discussions)
            ) { onOpenExternal(repository.htmlUrl.trimEnd('/') + "/discussions") }
            GithubCompactNavRow(
                icon = Icons.Default.PlayArrow,
                iconContainer = GithubSectionTints.actions,
                label = stringResource(R.string.github_view_actions)
            ) { onOpenActions(details) }
            GithubCompactNavRow(
                icon = Icons.Default.NewReleases,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_releases)
            ) { onOpenReleases(details) }
            GithubCompactNavRow(
                icon = Icons.Default.Code,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_commits)
            ) { onOpenCommits(details) }
            GithubCompactNavRow(
                icon = Icons.Default.AccountTree,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_branches)
            ) { onOpenBranches(details) }
            GithubCompactNavRow(
                icon = Icons.Default.LocalOffer,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_tags)
            ) { onOpenTags(details) }
            GithubCompactNavRow(
                icon = Icons.Default.Group,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_collaborators)
            ) {
                // Collaborator tiles fall back to the public contributors list, so the
                // page stays readable without push access.
                onOpenCollaborators(details)
            }
            GithubCompactNavRow(
                icon = Icons.Default.Link,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_webhooks),
                enabled = canAdminister || !isSignedIn,
                showDivider = false
            ) {
                when {
                    canAdminister -> onOpenWebhooks(details)
                    !isSignedIn -> onSignIn()
                }
            }
                }
            }
            Spacer(Modifier.height(8.dp))
            RepositorySettingsMenu(
                fullName = repository.fullName,
                onOpenExternal = onOpenExternal,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedButton(
                onClick = { onOpenExternal(repository.htmlUrl) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = GithubExpressiveShapes.control
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_open_on_github))
        }
    }
}

@Composable
private fun RepositoryBranchProtection(
    state: RepositoryDetailUiState,
    onRetry: () -> Unit
) {
    GithubSectionHeader(title = stringResource(R.string.github_branch_protection))
    when {
        state.isLoadingBranchProtection -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.branchProtectionError -> GithubMessageState(
            title = stringResource(R.string.github_branch_protection_error),
            color = MaterialTheme.colorScheme.error,
            actionLabel = stringResource(R.string.github_retry),
            onAction = onRetry
        )
        state.branchProtection != null -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = state.branchProtection.branch,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                GithubMetadataRow(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.github_required_checks),
                    value = state.branchProtection.requiredStatusChecks.toString(),
                    modifier = Modifier.padding(top = 8.dp)
                )
                GithubMetadataRow(
                    icon = Icons.Default.Balance,
                    title = stringResource(R.string.github_required_reviews),
                    value = state.branchProtection.requiredApprovingReviews?.toString()
                        ?: stringResource(R.string.github_not_configured)
                )
                GithubMetadataRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.github_enforce_admins),
                    value = stringResource(
                        if (state.branchProtection.enforceAdmins) R.string.github_enabled
                        else R.string.github_disabled
                    )
                )
            }
        }
    }
}

private data class SettingsRequest(
    val action: RepositoryDetailAction,
    val title: Int,
    val message: Int
)

private data class FeatureRow(
    val feature: GithubRepositoryFeature,
    val icon: ImageVector,
    val label: Int,
    val enabled: Boolean
)

@Composable
private fun RepositoryAdministration(
    details: GithubRepositoryDetails,
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit
) {
    if (!state.canManageSettings) return
    var pending by remember { mutableStateOf<SettingsRequest?>(null) }
    var descriptionDraft by remember { mutableStateOf<String?>(null) }
    var submittingDescription by remember { mutableStateOf(false) }
    // The dialog holds its draft open until the repository reply lands, so a refused write keeps what the admin typed.
    LaunchedEffect(submittingDescription, state.isUpdatingSettings, state.settingsFailure) {
        if (submittingDescription && !state.isUpdatingSettings && state.settingsFailure == null) {
            submittingDescription = false
            descriptionDraft = null
        }
    }
    var defaultBranchDraft by remember { mutableStateOf<String?>(null) }
    var submittingDefaultBranch by remember { mutableStateOf(false) }
    LaunchedEffect(submittingDefaultBranch, state.isUpdatingSettings, state.settingsFailure) {
        if (submittingDefaultBranch && !state.isUpdatingSettings && state.settingsFailure == null) {
            submittingDefaultBranch = false
            defaultBranchDraft = null
        }
    }
    val isPrivate = details.repository.isPrivate
    val fullName = details.repository.fullName
    val featureRows = listOf(
        FeatureRow(
            feature = GithubRepositoryFeature.Issues,
            icon = Icons.Default.BugReport,
            label = R.string.github_feature_issues,
            enabled = details.features.hasIssues
        ),
        FeatureRow(
            feature = GithubRepositoryFeature.Wiki,
            icon = Icons.Default.MenuBook,
            label = R.string.github_feature_wiki,
            enabled = details.features.hasWiki
        ),
        FeatureRow(
            feature = GithubRepositoryFeature.Projects,
            icon = Icons.Default.AccountTree,
            label = R.string.github_feature_projects,
            enabled = details.features.hasProjects
        )
    )
    GithubSectionHeader(title = stringResource(R.string.github_repository_administration))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            AdministrationToggle(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.github_repository_visibility),
                summary = stringResource(
                    if (isPrivate) R.string.github_visibility_private else R.string.github_visibility_public
                ),
                checked = isPrivate,
                // GitHub rejects every other repository edit while it is archived.
                enabled = !state.isUpdatingSettings && !details.isArchived,
                onClick = {
                    val target = !isPrivate
                    pending = SettingsRequest(
                        action = RepositoryDetailAction.SetVisibility(target),
                        title = if (target) R.string.github_make_private else R.string.github_make_public,
                        message = if (target) {
                            R.string.github_make_private_message
                        } else {
                            R.string.github_make_public_message
                        }
                    )
                }
            )
            AdministrationToggle(
                icon = Icons.Default.Archive,
                title = stringResource(R.string.github_archive_repository),
                summary = stringResource(
                    if (details.isArchived) R.string.github_archived_repository else R.string.github_active_repository
                ),
                checked = details.isArchived,
                enabled = !state.isUpdatingSettings,
                onClick = {
                    val target = !details.isArchived
                    pending = SettingsRequest(
                        action = RepositoryDetailAction.SetArchived(target),
                        title = if (target) R.string.github_archive_repository else R.string.github_unarchive_repository,
                        message = if (target) {
                            R.string.github_archive_message
                        } else {
                            R.string.github_unarchive_message
                        }
                    )
                }
            )
            featureRows.forEach { row ->
                AdministrationToggle(
                    icon = row.icon,
                    title = stringResource(row.label),
                    summary = stringResource(
                        if (row.enabled) R.string.github_enabled else R.string.github_disabled
                    ),
                    checked = row.enabled,
                    enabled = !state.isUpdatingSettings && !details.isArchived,
                    onClick = { onAction(RepositoryDetailAction.SetFeature(row.feature, !row.enabled)) }
                )
            }
            AdministrationEditRow(
                icon = Icons.Default.Description,
                title = stringResource(R.string.github_repository_description),
                summary = details.repository.description
                    ?: stringResource(R.string.github_no_description),
                enabled = !state.isUpdatingSettings && !details.isArchived,
                onClick = { descriptionDraft = details.repository.description.orEmpty() }
            )
            AdministrationEditRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(R.string.github_default_branch),
                summary = details.defaultBranch,
                enabled = !state.isUpdatingSettings && !details.isArchived,
                onClick = { defaultBranchDraft = details.defaultBranch }
            )
            state.settingsFailure?.takeIf { descriptionDraft == null && defaultBranchDraft == null }?.let { failure ->
                Text(
                    text = repositoryActionFailureText(failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
    pending?.let { request ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) pending = null
            },
            title = { Text(stringResource(request.title)) },
            text = { Text(stringResource(request.message, fullName)) },
            confirmButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings,
                    onClick = {
                        pending = null
                        onAction(request.action)
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings,
                    onClick = { pending = null }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    descriptionDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) {
                    submittingDescription = false
                    descriptionDraft = null
                }
            },
            title = { Text(stringResource(R.string.github_edit_description)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { descriptionDraft = it },
                        enabled = !state.isUpdatingSettings,
                        singleLine = false,
                        minLines = 2,
                        label = { Text(stringResource(R.string.github_description_hint)) }
                    )
                    state.settingsFailure?.let { failure ->
                        Text(
                            text = repositoryActionFailureText(failure),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings,
                    onClick = {
                        submittingDescription = true
                        onAction(RepositoryDetailAction.UpdateDescription(draft))
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings,
                    onClick = {
                        submittingDescription = false
                        descriptionDraft = null
                    }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    defaultBranchDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) {
                    submittingDefaultBranch = false
                    defaultBranchDraft = null
                }
            },
            title = { Text(stringResource(R.string.github_edit_default_branch)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { defaultBranchDraft = it },
                        enabled = !state.isUpdatingSettings,
                        singleLine = true,
                        label = { Text(stringResource(R.string.github_default_branch_hint)) }
                    )
                    state.settingsFailure?.let { failure ->
                        Text(
                            text = repositoryActionFailureText(failure),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings && draft.isNotBlank(),
                    onClick = {
                        submittingDefaultBranch = true
                        onAction(RepositoryDetailAction.SetDefaultBranch(draft))
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isUpdatingSettings,
                    onClick = {
                        submittingDefaultBranch = false
                        defaultBranchDraft = null
                    }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

@Composable
private fun AdministrationRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

@Composable
private fun AdministrationToggle(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AdministrationRow(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = {
            // The switch only ever reflects what the repository confirmed, so a refused
            // or still pending write cannot leave it showing a state that never happened.
            Switch(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        },
        onClick = onClick
    )
}

@Composable
private fun AdministrationEditRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AdministrationRow(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}

private object GithubSectionTints {
    val issues = Color(0xFF3FB950)
    val pulls = Color(0xFF539BF5)
    val discussions = Color(0xFFAB7DF8)
    val actions = Color(0xFFD4A72C)
    val neutral = Color(0xFF8B949E)
}

@Composable
private fun GithubInlineStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun GithubCompactNavRow(
    icon: ImageVector,
    iconContainer: Color,
    label: String,
    count: String? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val nothing = LocalDesignStyle.current == DesignStyle.NOTHING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = GithubExpressiveShapes.compact,
            color = if (nothing) Color.Transparent else iconContainer.copy(alpha = contentAlpha),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (nothing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        else Color.White.copy(alpha = contentAlpha),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalContentColor.current.copy(alpha = contentAlpha),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun RepositorySettingsMenu(
    fullName: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.control
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.github_repository_settings))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                R.string.github_settings_general to GithubWebUrls.repositorySettings(fullName),
                R.string.github_settings_branches to GithubWebUrls.repositoryBranchesSettings(fullName),
                R.string.github_settings_actions to GithubWebUrls.repositoryActionsSettings(fullName),
                R.string.github_settings_collaborators to GithubWebUrls.repositoryCollaboratorsSettings(fullName),
                R.string.github_settings_webhooks to GithubWebUrls.repositoryWebhooksSettings(fullName)
            ).forEach { (label, url) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onOpenExternal(url)
                    }
                )
            }
        }
    }
}

@Composable
private fun RepositoryTopicsEditor(
    topics: List<String>,
    isUpdating: Boolean,
    failure: RepositoryWriteFailure?,
    enabled: Boolean,
    onUpdate: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    // A submission stays pending until the repository reply lands, so the dialog can
    // close on success and hold its draft open on failure.
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(isUpdating, failure) {
        if (pending && !isUpdating && failure == null) {
            pending = false
            open = false
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                text = topics.joinToString(", ")
                open = true
            },
            enabled = enabled && !isUpdating,
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.control
        ) {
            Text(stringResource(R.string.github_edit_topics))
        }
        if (failure != null && !open) {
            TopicsFailureNotice(failure = failure, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = {
                if (!isUpdating) {
                    pending = false
                    open = false
                }
            },
            title = { Text(stringResource(R.string.github_edit_topics)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        enabled = !isUpdating,
                        singleLine = false,
                        minLines = 2,
                        label = { Text(stringResource(R.string.github_topics_hint)) }
                    )
                    if (failure != null) {
                        TopicsFailureNotice(failure = failure, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = true
                        onUpdate(
                            text.split(',')
                                .map(String::trim)
                                .filter(String::isNotBlank)
                        )
                    },
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pending = false
                        open = false
                    },
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

@Composable
private fun TopicsFailureNotice(failure: RepositoryWriteFailure, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.github_topics_update_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = repositoryActionFailureText(failure),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RepositoryTag(text: String, icon: ImageVector? = null) {
    Surface(
        shape = GithubExpressiveShapes.control,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun RepositoryReadme(
    details: GithubRepositoryDetails,
    state: RepositoryDetailUiState,
    onRetry: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    GithubSectionHeader(title = stringResource(R.string.github_readme))
    when {
        state.isLoadingReadme -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        state.readmeError -> {
            GithubMessageState(
                title = stringResource(R.string.github_readme_load_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = onRetry
            )
        }
        state.readme.isNullOrBlank() -> {
            GithubMessageState(title = stringResource(R.string.github_no_readme))
        }
        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = GithubExpressiveShapes.container,
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                MarkdownPreviewText(
                    markdown = state.readme,
                    imageBitmaps = emptyMap(),
                    onOpenExternalLink = { link ->
                        onOpenExternal(
                            GithubWebUrls.resolveMarkdownLink(
                                fullName = details.repository.fullName,
                                ref = details.defaultBranch,
                                sourcePath = "",
                                target = link
                            )
                        )
                    },
                    renderImages = false,
                    maxElements = 160,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
