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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPreferenceRow
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.navigation.GithubWebUrls

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
    GithubDetailScaffold(
        title = state.login,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item(key = "profile-header") {
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
                        onToggleFollowing = { onAction(PublicUserProfileAction.ToggleFollowing) },
                        onRetryFollowing = { onAction(PublicUserProfileAction.RetryFollowing) },
                        onSignIn = onSignIn,
                        onOpenExternal = onOpenExternal
                    )
                    state.isLoadingUser -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.userError -> GithubMessageState(
                        title = stringResource(R.string.github_public_profile_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(PublicUserProfileAction.RetryUser) }
                    )
                }
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
                item(key = "repositories-loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
                    onLoadMore = { onAction(PublicUserProfileAction.LoadMore) }
                )
            }
        }
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
    onToggleFollowing: () -> Unit,
    onRetryFollowing: () -> Unit,
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
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
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
            GithubMetric(
                user.following.toString(),
                stringResource(R.string.github_following),
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
                onClick = onOpenFollowing
            )
        }
        PublicUserFollowingAction(
            profileLogin = user.login,
            viewerLogin = viewerLogin,
            isFollowing = isFollowing,
            isLoading = isLoadingFollowing,
            isUpdating = isUpdatingFollowing,
            hasError = followingError,
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
                    text = stringResource(R.string.github_follow_error),
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
            text = stringResource(R.string.github_follow_error),
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
