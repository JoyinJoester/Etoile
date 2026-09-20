package takagi.ru.monica.github.feature.store

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Link
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubPreferenceGroup
import takagi.ru.monica.github.component.GithubPreferenceRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubSearchField
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.component.GithubTechnicalSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalTag
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.FdroidApp
import takagi.ru.monica.github.domain.GithubApkPackage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubStoreApp
import takagi.ru.monica.github.domain.toApkPackages
import takagi.ru.monica.ui.components.MarkdownPreviewText
import java.io.File

/**
 * 类 Droid-ify 的商店页：推荐目录来自高星 Android 开源仓库，可切换到
 * F-Droid 索引目录，也支持添加自定义仓库来源；应用详情解析最新 Release
 * 的 APK(文件名 + 包体真身)，下载后可拉起系统安装器。
 */

/**
 * 商店目录:推荐/F-Droid 标签页、来源区与应用磁贴。
 */
@Composable
internal fun StoreRecommendedCatalog(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit,
    header: @Composable () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "catalog-header", span = { GridItemSpan(maxLineSpan) }) {
            header()
        }
        item(key = "store-sources", span = { GridItemSpan(maxLineSpan) }) {
            StoreSourceSection(state = state, onAction = onAction)
        }
        if (state.isLoading) {
            item(key = "store-loading", span = { GridItemSpan(maxLineSpan) }) {
                GithubListLoadingState(
                    isLoading = true,
                    hasItems = state.filteredApps.isNotEmpty(),
                    row = GithubSkeletonRow.LIST
                )
            }
        }
        items(state.filteredApps, key = { it.id }) { repository ->
            StoreAppTile(
                repository = repository,
                onClick = { onAction(StoreAction.OpenApp(repository)) }
            )
        }
        item(key = "store-status", span = { GridItemSpan(maxLineSpan) }) {
            GithubPagedListStatus(
                itemCount = state.filteredApps.size,
                isInitialLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                hasError = state.error,
                canLoadMore = state.canLoadMore,
                errorMessage = stringResource(R.string.github_store_error),
                emptyMessage = stringResource(R.string.github_store_empty),
                onRetry = { onAction(StoreAction.Retry) },
                emptyIcon = Icons.Default.Android,
                onLoadMore = { onAction(StoreAction.LoadMore) }
            )
        }
    }
}

@Composable
internal fun StoreFdroidCatalog(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit,
    header: @Composable () -> Unit
) {
    GithubAdaptiveGrid(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
    ) {
        githubFullSpanItem(key = "catalog-header") { header() }
        if (state.isLoadingFdroid) {
            githubFullSpanItem(key = "fdroid-loading") {
                GithubListLoadingState(
                    isLoading = true,
                    hasItems = state.filteredFdroidApps.isNotEmpty() || state.updates.isNotEmpty(),
                    row = GithubSkeletonRow.LIST,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        if (state.fdroidError) {
            githubFullSpanItem(key = "fdroid-error") {
                GithubPagedListStatus(
                    itemCount = state.filteredFdroidApps.size + state.updates.size,
                    isInitialLoading = false,
                    isLoadingMore = false,
                    hasError = true,
                    canLoadMore = false,
                    errorMessage = stringResource(R.string.github_store_fdroid_error),
                    emptyMessage = stringResource(R.string.github_store_empty),
                    onRetry = { onAction(StoreAction.RetryFdroid) },
                    emptyIcon = Icons.Default.Android,
                    onLoadMore = {}
                )
            }
        }
        if (state.updates.isNotEmpty() && state.fdroidFilter == FdroidCatalogFilter.ALL && state.query.isBlank()) {
            githubFullSpanItem(key = "fdroid-updates-header") {
                GithubTechnicalSectionHeader(
                    label = stringResource(R.string.github_store_updates),
                    trailing = {
                        GithubTechnicalLabel(text = state.updates.size.toString())
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            items(state.updates, key = { "update-${it.packageName}" }) { app ->
                StoreFdroidRow(
                    app = app,
                    installedVersion = state.fdroidInstalled[app.packageName],
                    onClick = { onAction(StoreAction.OpenFdroidApp(app)) }
                )
            }
        }
        items(
            state.filteredFdroidApps,
            key = { it.packageName }
        ) { app ->
            StoreFdroidRow(
                app = app,
                installedVersion = state.fdroidInstalled[app.packageName],
                onClick = { onAction(StoreAction.OpenFdroidApp(app)) }
            )
        }
        // An update-only catalog is still useful content; don't cover it with
        // the global empty state while the updates section is visible.
        if (state.filteredFdroidApps.isEmpty() && (state.updates.isEmpty() || state.fdroidFilter != FdroidCatalogFilter.ALL || state.query.isNotBlank()) &&
            !state.isLoadingFdroid && !state.fdroidError) {
            githubFullSpanItem(key = "fdroid-empty") {
                GithubPagedListStatus(
                    itemCount = 0,
                    isInitialLoading = false,
                    isLoadingMore = false,
                    hasError = false,
                    canLoadMore = false,
                    errorMessage = stringResource(R.string.github_store_fdroid_error),
                    emptyMessage = stringResource(R.string.github_store_empty),
                    onRetry = { onAction(StoreAction.RetryFdroid) },
                    emptyIcon = Icons.Default.Android,
                    onLoadMore = {}
                )
            }
        }
    }
}

@Composable
internal fun StoreFdroidRow(
    app: FdroidApp,
    installedVersion: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = GithubExpressiveShapes.compact,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            takagi.ru.monica.github.component.GithubAvatar(
                login = app.name, avatarUrl = app.iconUrl, size = 44.dp,
                shape = GithubExpressiveShapes.compact
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                app.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GithubTechnicalLabel(
                    text = app.sourceName,
                    modifier = Modifier.padding(top = 2.dp)
                )
                val badge = installedBadge(installedVersion, app.latestVersionName)
                val versionLabel = badge ?: app.latestVersionName?.let { "v$it" }
                versionLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/** 已安装徽标：与最新版本一致显示"已安装"，落后则显示"可更新"。 */
@Composable
internal fun installedBadge(
    installedVersion: String?,
    latestVersion: String?
): String? {
    val installed = installedVersion ?: return null
    return if (latestVersion != null && installed != latestVersion) {
        stringResource(R.string.github_store_update_available, latestVersion)
    } else {
        stringResource(R.string.github_store_installed, installed)
    }
}

/** 自定义来源区：用户添加的仓库以磁贴呈现，可随时移除，支持粘贴仓库链接。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoreSourceSection(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit
) {
    if (LocalDesignStyle.current != DesignStyle.MATERIAL) {
        StoreSourceEditor(state, onAction)
        return
    }
    var showSources by rememberSaveable { mutableStateOf(false) }
    GithubPreferenceGroup {
        GithubPreferenceRow(
            icon = Icons.Default.Link,
            title = stringResource(R.string.github_store_my_sources),
            value = "${state.sources.size} · ${stringResource(R.string.github_manage)}",
            onClick = { showSources = true }
        )
    }
    if (showSources) {
        GithubModalBottomSheet(onDismissRequest = { showSources = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                GithubSectionHeader(stringResource(R.string.github_store_my_sources), compact = true)
                // Editing sources is independent of the catalog's current search query.
                StoreSourceEditor(state.copy(query = "")) { action ->
                    if (action is StoreAction.OpenApp) showSources = false
                    onAction(action)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreSourceEditor(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
            androidx.compose.material3.OutlinedTextField(
                value = state.sourceInput,
                onValueChange = { onAction(StoreAction.SourceInputChanged(it)) },
                label = { Text(stringResource(R.string.github_store_source_hint)) },
                singleLine = true,
                isError = state.sourceInputError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (state.sourceInput.isNotBlank() && !state.isLoadingSources) {
                        onAction(StoreAction.AddSource)
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = GithubExpressiveShapes.control,
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { onAction(StoreAction.AddSource) },
                        enabled = state.sourceInput.isNotBlank() && !state.isLoadingSources
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.github_store_add_source))
                    }
                }
            )
        } else {
            GithubSearchField(
                value = state.sourceInput,
                onValueChange = { onAction(StoreAction.SourceInputChanged(it)) },
                label = stringResource(R.string.github_store_source_hint),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (state.sourceInput.isNotBlank()) {
                    {
                        IconButton(onClick = { onAction(StoreAction.AddSource) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.github_store_add_source)
                            )
                        }
                    }
                } else {
                    null
                },
                compact = true
            )
        }
        if (state.sourceInputError) {
            Text(
                text = stringResource(R.string.github_store_source_invalid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
        if (state.sources.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (LocalDesignStyle.current != DesignStyle.MATERIAL) {
                    GithubTechnicalLabel(
                        text = stringResource(R.string.github_store_my_sources),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.filteredSources.forEach { source ->
                    Surface(
                        onClick = { onAction(StoreAction.OpenApp(source)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GithubExpressiveShapes.compact,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = source.fullName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { onAction(StoreAction.RemoveSource(source.fullName)) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.github_store_remove_source),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 商店磁贴：应用名 + 所有者 + 简介 + 星标，双列平铺。 */
@Composable
internal fun StoreAppTile(
    repository: GithubRepository,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.control,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    GithubTechnicalLabel(text = repository.fullName.substringBefore('/'))
                }
            }
            Text(
                text = repository.description ?: stringResource(R.string.github_no_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                GithubTechnicalLabel(text = repository.stars.toString())
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
                Spacer(Modifier.width(6.dp))
                GithubTechnicalLabel(
                    text = repository.language ?: stringResource(R.string.github_unknown_language)
                )
            }
        }
    }
}

