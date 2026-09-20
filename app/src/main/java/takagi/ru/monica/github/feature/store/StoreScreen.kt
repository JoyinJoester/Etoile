package takagi.ru.monica.github.feature.store

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
@Composable
fun StoreScreen(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.selectedFdroid != null || state.selected != null) {
        StoreReadingScreen(state, onAction, onOpenRepository, onOpenExternal, onInstallApk, modifier)
        return
    }
    var showSources by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showSources) StoreSourcesSheet(state, onAction) { showSources = false }
    val catalogHeader: @Composable () -> Unit = {
        Column {
            androidx.compose.material3.TextButton(onClick = { showSources = true }) {
                Text(stringResource(R.string.store_sources_count, state.enabledFdroidSources.size, takagi.ru.monica.github.domain.FdroidSources.builtIn.size))
            }
            if (state.failedFdroidSources.isNotEmpty()) {
                Text(stringResource(R.string.store_sources_failed, state.failedFdroidSources.joinToString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GithubSearchField(
                    value = state.query,
                    onValueChange = { onAction(StoreAction.Search(it)) },
                    label = stringResource(R.string.github_store_subtitle),
                    modifier = Modifier.weight(1f),
                    compact = true
                )
                IconButton(
                    onClick = {
                        onAction(if (state.catalogTab == StoreCatalogTab.FDROID) StoreAction.RetryFdroid else StoreAction.Refresh)
                    },
                    enabled = if (state.catalogTab == StoreCatalogTab.FDROID) !state.isLoadingFdroid
                        else !state.isLoading && !state.isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.github_store_refresh)
                    )
                }
            }
            GithubFilterRow(
                labels = listOf(
                    stringResource(R.string.github_store_tab_recommended),
                    stringResource(R.string.github_store_tab_fdroid)
                ),
                selectedIndex = StoreCatalogTab.entries.indexOf(state.catalogTab),
                onSelected = { onAction(StoreAction.CatalogTabSelected(StoreCatalogTab.entries[it])) },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (state.catalogTab == StoreCatalogTab.FDROID) {
                GithubFilterRow(labels = listOf(stringResource(R.string.store_all), stringResource(R.string.store_installed), stringResource(R.string.github_store_updates)),
                    selectedIndex = state.fdroidFilter.ordinal,
                    onSelected = { onAction(StoreAction.FilterFdroid(FdroidCatalogFilter.entries[it])) })
                Text(stringResource(R.string.store_app_count, state.filteredFdroidApps.size),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
    GithubPullToRefreshBox(
        isRefreshing = if (state.catalogTab == StoreCatalogTab.RECOMMENDED) {
            state.isRefreshing
        } else {
            state.isLoadingFdroid
        },
        onRefresh = {
            if (state.catalogTab == StoreCatalogTab.RECOMMENDED) {
                onAction(StoreAction.Refresh)
            } else {
                onAction(StoreAction.RetryFdroid)
            }
        },
        enabled = true,
        modifier = modifier.fillMaxSize()
    ) {
        when (state.catalogTab) {
            StoreCatalogTab.RECOMMENDED -> StoreRecommendedCatalog(
                state = state, onAction = onAction, header = catalogHeader
            )
            StoreCatalogTab.FDROID -> StoreFdroidCatalog(
                state = state, onAction = onAction, header = catalogHeader
            )
        }
    }
}
