package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubRefreshButton
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownImageContext
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun RepositoryDetailScreen(
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onBack: () -> Unit,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenMore: () -> Unit,
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
            GithubRefreshButton(
                onClick = { onAction(RepositoryDetailAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoadingDetails && !state.isUpdatingSettings
            )
            GithubOpenOnGithubButton(onClick = {
                onOpenExternal(details?.repository?.htmlUrl ?: GithubWebUrls.repository(state.fullName))
            })
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            when {
                details == null && state.isLoadingDetails -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                details == null -> GithubMessageState(
                    title = stringResource(R.string.github_repository_load_error),
                    color = MaterialTheme.colorScheme.error,
                    actionLabel = stringResource(R.string.github_retry),
                    onAction = { onAction(RepositoryDetailAction.RetryDetails) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                else -> {
                    val summary: @Composable () -> Unit = {
                        RepositorySummary(
                            details = details,
                            onBrowseCode = onBrowseCode,
                            onOpenIssues = onOpenIssues,
                            onOpenPullRequests = onOpenPullRequests,
                            onOpenReleases = onOpenReleases,
                            onOpenMore = onOpenMore,
                            actions = {
                                RepositoryActionControls(
                                    state = state,
                                    isSignedIn = isSignedIn,
                                    onAction = onAction,
                                    onSignIn = onSignIn,
                                    onOpenRepository = onOpenRepository,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        )
                    }
                    val readme: @Composable () -> Unit = {
                        RepositoryReadme(details, state,
                            onRetry = { onAction(RepositoryDetailAction.RetryReadme) },
                            onOpenExternal = onOpenExternal)
                    }
                    if (maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth *
                        androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)) {
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.width(360.dp).fillMaxHeight(), contentPadding = PaddingValues(20.dp)) {
                                item { summary() }
                            }
                            VerticalDivider(Modifier.fillMaxHeight())
                            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
                                item { readme() }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                            item { summary() }
                            item { readme() }
                        }
                    }
                }
            }
            if (state.isLoadingDetails && details != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun RepositorySummary(
    details: GithubRepositoryDetails,
    onBrowseCode: (GithubRepositoryDetails) -> Unit,
    onOpenIssues: (GithubRepositoryDetails) -> Unit,
    onOpenPullRequests: (GithubRepositoryDetails) -> Unit,
    onOpenReleases: (GithubRepositoryDetails) -> Unit,
    onOpenMore: () -> Unit,
    actions: @Composable () -> Unit
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
            actions()

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
                icon = Icons.Default.NewReleases,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_view_releases)
            ) { onOpenReleases(details) }
            GithubCompactNavRow(
                icon = Icons.Default.MoreHoriz,
                iconContainer = GithubSectionTints.neutral,
                label = stringResource(R.string.github_repository_more),
                showDivider = false
            ) { onOpenMore() }
                }
            }
    }
}

@Composable
internal fun RepositoryBranchProtection(
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

internal object GithubSectionTints {
    val issues = Color(0xFF3FB950)
    val pulls = Color(0xFF539BF5)
    val discussions = Color(0xFFAB7DF8)
    val actions = Color(0xFFD4A72C)
    val neutral = Color(0xFF8B949E)
}

@Composable
internal fun GithubCompactNavRow(
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
internal fun RepositorySettingsMenu(
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
internal fun RepositoryTopicsEditor(
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
                    imageContext = MarkdownImageContext(details.repository.fullName, details.defaultBranch),
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
