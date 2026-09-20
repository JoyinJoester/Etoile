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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubAdaptiveDetailLayout
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.GithubContributionHeatmap
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.component.GithubSkeletonList
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPreferenceRow
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubTechnicalTag
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun PublicUserProfileScreen(
    state: PublicUserProfileUiState,
    onAction: (PublicUserProfileAction) -> Unit,
    onBack: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    viewerLogin: String?,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBlockConfirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.login) { showBlockConfirm = false }
    LaunchedEffect(state.isBlocked, state.isUpdatingBlocked) {
        if (showBlockConfirm && !state.isUpdatingBlocked && state.isBlocked == true) {
            showBlockConfirm = false
        }
    }
    val identity: @Composable () -> Unit = {
        when {
            state.user != null -> PublicUserHeader(
                user = state.user,
                onOpenFollowers = onOpenFollowers,
                onOpenFollowing = onOpenFollowing,
                viewerLogin = viewerLogin,
                isFollowing = state.isFollowing,
                isLoadingFollowing = state.isLoadingFollowing,
                isUpdatingFollowing = state.isUpdatingFollowing,
                followingError = state.followingError,
                followingAccessError = state.followingAccessError,
                onToggleFollowing = { onAction(PublicUserProfileAction.ToggleFollowing) },
                onRetryFollowing = { onAction(PublicUserProfileAction.RetryFollowing) },
                isBlocked = state.isBlocked,
                isUpdatingBlocked = state.isUpdatingBlocked,
                blockedFailure = state.blockedFailure,
                onRequestBlock = { showBlockConfirm = true },
                onToggleBlocked = { onAction(PublicUserProfileAction.ToggleBlocked) },
                onRetryBlocked = { onAction(PublicUserProfileAction.RetryBlocked) },
                onSignIn = onSignIn,
                onOpenExternal = onOpenExternal
            )
            state.isLoadingUser -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.userError -> GithubMessageState(
                title = stringResource(R.string.github_public_profile_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(PublicUserProfileAction.RetryUser) }
            )
        }
    }
    GithubDetailScaffold(
        title = state.login,
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        subtitle = stringResource(R.string.github_public_profile),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.user(state.login))
            }
        }
    ) { padding ->
        GithubAdaptiveDetailLayout(
            modifier = Modifier.fillMaxSize().padding(padding),
            sidebar = { paneModifier ->
                LazyColumn(
                    modifier = paneModifier,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    item(key = "profile-header") { identity() }
                }
            }
        ) { paneModifier, expanded ->
            LazyColumn(
                modifier = paneModifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (!expanded) item(key = "profile-header") { identity() }
                item(key = "contributions") {
                    PublicUserContributionSection(state = state, onAction = onAction)
                }
                item(key = "readme") {
                    PublicUserReadmeSection(state = state, onAction = onAction, onOpenExternal = onOpenExternal)
                }
                item(key = "repositories-header") {
                    Text(
                        stringResource(R.string.github_public_repositories),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
                    )
                }
                if (state.isLoadingRepositories && state.repositories.isEmpty()) {
                    item(key = "repositories-loading") {
                        GithubSkeletonList(row = GithubSkeletonRow.LIST, rowCount = 4)
                    }
                }
                items(state.repositories, key = GithubRepository::id) { repository ->
                    GithubRepositoryRow(
                        repository = repository,
                        descriptionFallback = stringResource(R.string.github_no_description),
                        languageFallback = stringResource(R.string.github_unknown_language),
                        updatedFallback = stringResource(R.string.github_updated_recently),
                        onClick = { onOpenRepository(repository) }
                    )
                }
                item(key = "repositories-status") {
                    GithubPagedListStatus(
                        itemCount = state.repositories.size,
                        isInitialLoading = state.isLoadingRepositories,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.repositoriesError,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_public_repositories_error),
                        emptyMessage = stringResource(R.string.github_no_public_repositories),
                        onRetry = { onAction(PublicUserProfileAction.RetryRepositories) },
                        emptyIcon = Icons.Default.Folder,
                        onLoadMore = { onAction(PublicUserProfileAction.LoadMore) }
                    )
                }
            }
        }
    }
    if (showBlockConfirm) {
        val updating = state.isUpdatingBlocked
        AlertDialog(
            onDismissRequest = { if (!updating) showBlockConfirm = false },
            title = { Text(stringResource(R.string.github_block_confirm_title, state.login)) },
            text = {
                Column {
                    Text(stringResource(R.string.github_block_confirm_message))
                    state.blockedFailure?.let {
                        Text(
                            text = userBlockFailureText(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !updating, onClick = {
                    onAction(PublicUserProfileAction.ToggleBlocked)
                }) { Text(stringResource(R.string.github_block_user)) }
            },
            dismissButton = {
                TextButton(enabled = !updating, onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.discussion_cancel))
                }
            }
        )
    }
}

@Composable
private fun PublicUserContributionSection(
    state: PublicUserProfileUiState,
    onAction: (PublicUserProfileAction) -> Unit
) {
    if (state.user?.isOrganization == true) return
    if (state.calendar == null && !state.isLoadingCalendar && !state.calendarError) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                state.calendar != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.github_profile_last_year),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        GithubTechnicalLabel(
                            text = state.calendar.totalContributions.toString(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    GithubContributionHeatmap(
                        calendar = state.calendar,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                state.isLoadingCalendar -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                // GraphQL 需要登录令牌；未登录或令牌受限时静默隐藏热力图。
                state.calendarError && state.user != null -> Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.github_profile_calendar_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { onAction(PublicUserProfileAction.RetryCalendar) }
                    ) {
                        Text(stringResource(R.string.github_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicUserReadmeSection(
    state: PublicUserProfileUiState,
    onAction: (PublicUserProfileAction) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    when {
        state.isLoadingReadme -> Unit
        state.readme != null -> {
            Text(
                text = stringResource(R.string.github_profile_readme_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = GithubExpressiveShapes.container,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                MarkdownPreviewText(
                    markdown = state.readme,
                    onOpenExternalLink = onOpenExternal,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        // 无 README 时不展示；提供了手动重试入口兜底网络失败。
        else -> Unit
    }
}

@Composable
private fun PublicUserHeader(
    user: GithubPublicUser,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    viewerLogin: String?,
    isFollowing: Boolean?,
    isLoadingFollowing: Boolean,
    isUpdatingFollowing: Boolean,
    followingError: Boolean,
    followingAccessError: Boolean,
    onToggleFollowing: () -> Unit,
    onRetryFollowing: () -> Unit,
    isBlocked: Boolean?,
    isUpdatingBlocked: Boolean,
    blockedFailure: RepositoryWriteFailure?,
    onRequestBlock: () -> Unit,
    onToggleBlocked: () -> Unit,
    onRetryBlocked: () -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GithubAvatar(
                login = user.login,
                avatarUrl = user.avatarUrl,
                size = 72.dp,
                shape = GithubExpressiveShapes.container
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name ?: user.login, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.github_user_handle, user.login),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        user.bio?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GithubMetric(user.publicRepositories.toString(), stringResource(R.string.github_repositories), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            GithubMetric(
                user.followers.toString(),
                stringResource(R.string.github_followers),
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f),
                onClick = onOpenFollowers
            )
            if (!user.isOrganization) GithubMetric(
                user.following.toString(),
                stringResource(R.string.github_following),
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
                onClick = onOpenFollowing
            )
        }
        if (user.isOrganization) {
            OutlinedButton(onClick = { onOpenExternal(GithubWebUrls.user(user.login)) },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text(stringResource(R.string.github_manage_follow_web))
            }
        } else PublicUserFollowingAction(
            profileLogin = user.login,
            viewerLogin = viewerLogin,
            isFollowing = isFollowing,
            isLoading = isLoadingFollowing,
            isUpdating = isUpdatingFollowing,
            hasError = followingError,
            accessError = followingAccessError,
            onToggle = onToggleFollowing,
            onRetry = onRetryFollowing,
            onSignIn = onSignIn
        )
        if (!user.company.isNullOrBlank() || !user.location.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) {
                user.company?.takeIf(String::isNotBlank)?.let {
                    Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                user.location?.takeIf(String::isNotBlank)?.let {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (user.isHireable == true) {
            Surface(
                modifier = Modifier.padding(top = 12.dp),
                shape = GithubExpressiveShapes.control,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = stringResource(R.string.github_hireable),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        user.blog?.let { rawBlog ->
            GithubWebUrls.external(rawBlog)?.let { blogUrl ->
                GithubPreferenceRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.github_blog),
                    value = blogUrl,
                    modifier = Modifier.padding(top = 6.dp),
                    onClick = { onOpenExternal(blogUrl) }
                )
            }
        }
        PublicUserBlockAction(
            profileLogin = user.login,
            viewerLogin = viewerLogin,
            canBlock = !user.isOrganization,
            isBlocked = isBlocked,
            isUpdating = isUpdatingBlocked,
            failure = blockedFailure,
            onRequestBlock = onRequestBlock,
            onUnblock = onToggleBlocked,
            onRetry = onRetryBlocked
        )
    }
}

@Composable
private fun PublicUserFollowingAction(
    profileLogin: String,
    viewerLogin: String?,
    isFollowing: Boolean?,
    isLoading: Boolean,
    isUpdating: Boolean,
    hasError: Boolean,
    accessError: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit
) {
    if (viewerLogin?.equals(profileLogin, ignoreCase = true) == true) return
    when {
        viewerLogin == null -> {
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = GithubExpressiveShapes.control
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_sign_in_to_follow))
            }
        }
        isLoading -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
        isFollowing != null -> {
            val buttonModifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            if (isFollowing) {
                OutlinedButton(
                    onClick = onToggle,
                    enabled = !isUpdating,
                    modifier = buttonModifier,
                    shape = GithubExpressiveShapes.control
                ) {
                    FollowingButtonContent(
                        updating = isUpdating,
                        icon = Icons.Default.PersonRemove,
                        label = stringResource(R.string.github_unfollow)
                    )
                }
            } else {
                Button(
                    onClick = onToggle,
                    enabled = !isUpdating,
                    modifier = buttonModifier,
                    shape = GithubExpressiveShapes.control
                ) {
                    FollowingButtonContent(
                        updating = isUpdating,
                        icon = Icons.Default.PersonAdd,
                        label = stringResource(R.string.github_follow)
                    )
                }
            }
        }
        hasError -> {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    text = stringResource(if (accessError) R.string.github_follow_access_error else R.string.github_follow_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.github_retry))
                }
            }
        }
    }
    if (hasError && isFollowing != null) {
        Text(
            text = stringResource(if (accessError) R.string.github_follow_access_error else R.string.github_follow_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text(stringResource(R.string.github_retry))
        }
    }
}

@Composable
private fun FollowingButtonContent(
    updating: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    if (updating) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Icon(icon, contentDescription = null)
    }
    Spacer(Modifier.width(8.dp))
    Text(label)
}

@Composable
private fun PublicUserBlockAction(
    profileLogin: String,
    viewerLogin: String?,
    canBlock: Boolean,
    isBlocked: Boolean?,
    isUpdating: Boolean,
    failure: RepositoryWriteFailure?,
    onRequestBlock: () -> Unit,
    onUnblock: () -> Unit,
    onRetry: () -> Unit
) {
    if (viewerLogin == null || viewerLogin.equals(profileLogin, ignoreCase = true)) return
    if (isBlocked == null) {
        failure?.let {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                BlockFailureMessage(it)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.github_retry)) }
            }
        }
        return
    }
    val controlsModifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    if (isBlocked) {
        Row(modifier = controlsModifier, verticalAlignment = Alignment.CenterVertically) {
            GithubTechnicalTag(label = stringResource(R.string.github_user_blocked_short))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onUnblock, enabled = !isUpdating) {
                UnblockButtonContent(updating = isUpdating)
            }
        }
    } else if (canBlock) {
        TextButton(
            onClick = onRequestBlock,
            enabled = !isUpdating,
            modifier = controlsModifier
        ) {
            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.github_block_user))
        }
    }
    failure?.let { BlockFailureMessage(it) }
}

@Composable
private fun UnblockButtonContent(updating: Boolean) {
    if (updating) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.github_unblock_user))
}

@Composable
private fun BlockFailureMessage(failure: RepositoryWriteFailure) {
    Text(
        text = userBlockFailureText(failure),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp)
    )
}
