package takagi.ru.monica.github.feature.releases

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubUserLink
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun ReleasesScreen(
    state: ReleasesUiState,
    onAction: (ReleasesAction) -> Unit,
    onBack: () -> Unit,
    onOpenRelease: (GithubRelease) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = state.name,
        subtitle = stringResource(R.string.github_releases),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.releases(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = GithubRelease::id) { release ->
                    ReleaseListCard(
                        release = release,
                        onClick = { onOpenRelease(release) }
                    )
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_release_list_error),
                        emptyMessage = stringResource(R.string.github_no_releases),
                        onRetry = { onAction(ReleasesAction.Retry) },
                        onLoadMore = { onAction(ReleasesAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseListCard(
    release: GithubRelease,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            if (release.isDraft || release.isPrerelease) {
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (release.isDraft) {
                        ReleaseBadge(stringResource(R.string.github_release_draft), isProminent = true)
                    }
                    if (release.isPrerelease) {
                        ReleaseBadge(stringResource(R.string.github_release_prerelease))
                    }
                }
            }

            release.body?.trim()?.takeIf(String::isNotEmpty)?.let { body ->
                Text(
                    text = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().removePrefix("#").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = release.publishedAt?.take(10) ?: release.createdAt.take(10),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.github_release_assets_count,
                        release.assets.size,
                        release.assets.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReleaseBadge(text: String, isProminent: Boolean = false) {
    Surface(
        shape = GithubExpressiveShapes.control,
        color = if (isProminent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isProminent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ReleaseDetailScreen(
    state: ReleaseDetailUiState,
    onAction: (ReleaseDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val release = state.release
    GithubDetailScaffold(
        title = release?.tagName ?: stringResource(R.string.github_release),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            release?.let {
                GithubOpenOnGithubButton(onClick = { onOpenExternal(it.htmlUrl) })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                release == null && state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                release == null && state.error -> {
                    GithubMessageState(
                        title = stringResource(R.string.github_release_load_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(ReleaseDetailAction.Retry) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                release != null -> {
                    ReleaseDetailContent(
                        release = release,
                        fullName = state.fullName,
                        onOpenExternal = onOpenExternal,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
            if (state.isLoading && release != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun ReleaseDetailContent(
    release: GithubRelease,
    fullName: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.widthIn(max = GithubAdaptiveLayout.contentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item(key = "summary") {
            ReleaseSummary(release)
        }
        item(key = "notes-heading") {
            GithubSectionHeader(title = stringResource(R.string.github_release_notes))
        }
        item(key = "notes") {
            if (release.body.isNullOrBlank()) {
                GithubMessageState(title = stringResource(R.string.github_release_no_notes))
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GithubExpressiveShapes.container,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    MarkdownPreviewText(
                        markdown = release.body,
                        imageBitmaps = emptyMap(),
                        onOpenExternalLink = { link ->
                            onOpenExternal(
                                GithubWebUrls.resolveMarkdownLink(
                                    fullName = fullName,
                                    ref = release.targetCommitish,
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
        item(key = "assets-heading") {
            GithubSectionHeader(title = stringResource(R.string.github_release_assets))
        }
        if (release.assets.isEmpty()) {
            item(key = "no-assets") {
                GithubMessageState(title = stringResource(R.string.github_release_no_assets))
            }
        } else {
            items(release.assets, key = GithubReleaseAsset::id) { asset ->
                ReleaseAssetRow(asset = asset, onOpenExternal = onOpenExternal)
                Spacer(Modifier.height(10.dp))
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseSummary(release: GithubRelease) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (release.isDraft || release.isPrerelease) {
                FlowRow(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (release.isDraft) {
                        ReleaseBadge(stringResource(R.string.github_release_draft), isProminent = true)
                    }
                    if (release.isPrerelease) {
                        ReleaseBadge(stringResource(R.string.github_release_prerelease))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GithubMetadataRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.github_release_author),
                value = release.author.login,
                valueContent = { GithubUserLink(release.author.login, avatarUrl = release.author.avatarUrl) }
            )
            GithubMetadataRow(
                icon = Icons.Default.Schedule,
                title = stringResource(
                    if (release.publishedAt == null) {
                        R.string.github_release_created
                    } else {
                        R.string.github_release_published
                    }
                ),
                value = (release.publishedAt ?: release.createdAt).take(10)
            )
            GithubMetadataRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(R.string.github_release_target),
                value = release.targetCommitish
            )
            GithubMetadataRow(
                icon = Icons.Default.Inventory2,
                title = stringResource(R.string.github_release_assets),
                value = pluralStringResource(
                    R.plurals.github_release_assets_count,
                    release.assets.size,
                    release.assets.size
                )
            )
        }
    }
}

@Composable
private fun ReleaseAssetRow(
    asset: GithubReleaseAsset,
    onOpenExternal: (String) -> Unit
) {
    val context = LocalContext.current
    val formattedSize = Formatter.formatShortFileSize(context, asset.sizeBytes)
    Surface(
        onClick = { onOpenExternal(asset.downloadUrl) },
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = GithubExpressiveShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.label?.takeIf(String::isNotBlank) ?: asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!asset.label.isNullOrBlank()) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.github_release_asset_metadata,
                        formattedSize,
                        pluralStringResource(
                            R.plurals.github_release_downloads_count,
                            asset.downloadCount,
                            asset.downloadCount
                        )
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.github_release_download_asset, asset.name),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
