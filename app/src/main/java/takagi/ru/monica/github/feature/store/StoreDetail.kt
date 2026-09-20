package takagi.ru.monica.github.feature.store

import androidx.compose.foundation.layout.widthIn
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.GithubAdaptiveDetailLayout
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubCenteredProgress
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
 * 商店详情:GitHub 仓库与 F-Droid 应用详情、APK 下载/解析/安装控件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoreAppDetail(
    app: GithubStoreApp,
    isLoading: Boolean,
    hasError: Boolean,
    installedVersion: String?,
    downloads: Map<String, ApkDownloadUiState>,
    onAction: (StoreAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = app.repository
    val repoKey = repository.fullName
    val summary: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = repository.description
                            ?: stringResource(R.string.github_no_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Store descriptions often contain the actual install caveats; keep
                        // enough vertical rhythm to read them without truncating the context.
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GithubTechnicalTag(label = "${repository.stars} ★")
                    GithubTechnicalTag(
                        label = repository.language
                            ?: stringResource(R.string.github_unknown_language)
                    )
                    installedVersion?.let {
                        GithubTechnicalTag(label = it, selected = true)
                    }
                }
            }
        }
    }
    val repositoryLink: @Composable () -> Unit = {
        Surface(
            onClick = { onOpenRepository(repository) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = stringResource(R.string.github_store_open_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    val downloadItems: LazyListScope.() -> Unit = {
        val apks = app.apkAssets.toApkPackages()
        item(key = "apk-header") {
            GithubSectionHeader(
                title = stringResource(R.string.github_store_download),
                compact = true
            )
        }
        if (apks.isEmpty()) {
            item(key = "apk-empty") {
                Text(
                    text = stringResource(R.string.github_store_no_apk),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            listItems(apks, key = { it.asset.id }) { apk ->
                ApkAssetRow(
                    apk = apk,
                    download = downloads[apk.asset.id.toString()],
                    onDownload = {
                        onAction(
                            StoreAction.DownloadApk(
                                key = apk.asset.id.toString(),
                                url = apk.asset.downloadUrl,
                                fileName = apk.asset.name,
                                repoKey = repoKey
                            )
                        )
                    },
                    onInstallApk = onInstallApk
                )
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        StoreDetailHeader(
            title = repository.name,
            subtitle = repository.fullName,
            onBack = { onAction(StoreAction.CloseApp) }
        )
        GithubAdaptiveDetailLayout(
            modifier = Modifier.fillMaxSize(),
            sidebar = { paneModifier ->
                LazyColumn(
                    modifier = paneModifier,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
                ) {
                    item(key = "summary") { summary() }
                    if (app.latestRelease != null) downloadItems()
                    item(key = "open-repo") { repositoryLink() }
                }
            }
        ) { paneModifier, expanded ->
            LazyColumn(
                modifier = paneModifier,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                if (!expanded) item(key = "summary") { summary() }
                item(key = "release-header") {
                    GithubTechnicalSectionHeader(
                        label = stringResource(R.string.github_store_latest),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                when {
                    isLoading -> item(key = "release-loading") {
                        GithubCenteredProgress()
                    }
                    hasError -> item(key = "release-error") {
                        GithubMessageState(
                            title = stringResource(R.string.github_release_load_error),
                            actionLabel = stringResource(R.string.github_retry),
                            onAction = { onAction(StoreAction.RetryDetail) }
                        )
                    }
                    app.latestRelease == null -> item(key = "release-missing") {
                        Text(
                            text = stringResource(R.string.github_store_no_apk),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    else -> {
                        val release = app.latestRelease
                        item(key = "release-info") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = GithubExpressiveShapes.container,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        GithubTechnicalTag(label = release.tagName)
                                        if (release.isPrerelease) {
                                            GithubTechnicalTag(label = stringResource(R.string.github_release_prerelease))
                                        }
                                    }
                                    release.name?.takeIf(String::isNotBlank)?.let { title ->
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 10.dp)
                                        )
                                    }
                                    release.body?.takeIf(String::isNotBlank)?.let { body ->
                                        MarkdownPreviewText(
                                            markdown = body,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (!expanded) downloadItems()
                    }
                }
                if (!expanded) item(key = "open-repo") { repositoryLink() }
            }
        }
    }
}

@Composable
internal fun StoreFdroidDetail(
    app: FdroidApp,
    installedVersion: String?,
    downloads: Map<String, ApkDownloadUiState>,
    onAction: (StoreAction) -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = GithubAdaptiveLayout.formMaxWidth).fillMaxSize()) {
            StoreDetailHeader(
                title = app.name,
                subtitle = app.packageName,
                onBack = { onAction(StoreAction.CloseApp) }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                item(key = "summary") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = GithubExpressiveShapes.container,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = app.summary
                                        ?: stringResource(R.string.github_no_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                app.latestVersionName?.let {
                                    GithubTechnicalTag(label = "v$it")
                                }
                                installedVersion?.let {
                                    GithubTechnicalTag(label = it, selected = true)
                                }
                            }
                        }
                    }
                }
                app.apkUrl?.let { apkUrl ->
                    val key = app.packageName
                    val download = downloads[key]
                    item(key = "fdroid-download") {
                        GithubSectionHeader(
                            title = stringResource(R.string.github_store_download),
                            compact = true
                        )
                        when {
                            download == null -> Button(
                                onClick = {
                                    onAction(
                                        StoreAction.DownloadApk(
                                            key = key,
                                            url = apkUrl,
                                            fileName = app.apkName ?: "${app.packageName}.apk",
                                            repoKey = app.packageName
                                        )
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.github_store_download))
                            }
                            else -> ApkDownloadControls(
                                state = download,
                                onDownload = {
                                    onAction(
                                        StoreAction.DownloadApk(
                                            key = key,
                                            url = apkUrl,
                                            fileName = app.apkName ?: "${app.packageName}.apk",
                                            repoKey = app.packageName
                                        )
                                    )
                                },
                                onInstallApk = onInstallApk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StoreDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.github_back)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            GithubTechnicalLabel(text = subtitle)
        }
    }
}

/** APK 资产行：文件名解析信息 + 下载/解析/安装状态。 */
@Composable
internal fun ApkAssetRow(
    apk: GithubApkPackage,
    download: ApkDownloadUiState?,
    onDownload: () -> Unit,
    onInstallApk: (File) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.compact,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apk.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val summary = listOfNotNull(
                        apk.version?.let { "v$it" },
                        apk.abi,
                        formatBytes(apk.asset.sizeBytes)
                    ).joinToString(" · ")
                    GithubTechnicalLabel(text = summary)
                }
                when {
                    download?.progress != null -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp)
                    )
                    download?.file != null -> GithubTechnicalTag(
                        label = stringResource(R.string.github_store_download)
                    )
                    else -> IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.GetApp,
                            contentDescription = stringResource(R.string.github_store_download),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            download?.let { state ->
                ApkDownloadControls(
                    state = state,
                    onDownload = onDownload,
                    onInstallApk = onInstallApk
                )
            }
        }
    }
}

/** APK 下载/解析结果区：进度条、解析出的包信息与安装按钮。 */
@Composable
internal fun ApkDownloadControls(
    state: ApkDownloadUiState,
    onDownload: () -> Unit,
    onInstallApk: ((File) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        state.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            GithubTechnicalLabel(
                text = "${(progress * 100).toInt()}%",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        when {
            state.details != null -> {
                val details = state.details
                GithubTechnicalLabel(
                    text = details.appName ?: details.packageName,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                GithubTechnicalLabel(
                    text = "${stringResource(R.string.github_store_package_label)}: ${details.packageName}",
                    modifier = Modifier.padding(top = 2.dp)
                )
                val summary = listOfNotNull(
                    details.versionName?.let { "v$it" },
                    details.minSdk?.let { stringResource(R.string.github_store_min_sdk, it) },
                    stringResource(R.string.github_store_permissions_count, details.permissions.size)
                ).joinToString(" · ")
                GithubTechnicalLabel(text = summary, modifier = Modifier.padding(top = 2.dp))
                details.permissions.takeIf { it.isNotEmpty() }?.let { permissions ->
                    Text(
                        text = permissions.joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (onInstallApk != null) {
                    state.file?.let { file ->
                        Button(
                            onClick = { onInstallApk(file) },
                            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.github_store_install))
                        }
                    }
                }
            }
            state.error -> {
                Text(
                    text = stringResource(R.string.github_store_download_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
                androidx.compose.material3.TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.github_store_refresh))
                }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
    else -> "$bytes B"
}

