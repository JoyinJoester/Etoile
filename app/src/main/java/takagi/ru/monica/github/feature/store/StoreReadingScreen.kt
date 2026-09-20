package takagi.ru.monica.github.feature.store

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.design.GithubExpressiveShapes
import java.io.File

/** Independent reader-style detail destination; the catalog remains underneath in navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreReadingScreen(
    state: StoreUiState,
    onAction: (StoreAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onAction(StoreAction.CloseApp) }
    AnimatedContent(targetState = state, contentKey = { it.selected?.repository?.fullName ?: it.selectedFdroid?.packageName },
        transitionSpec = {
            (slideInVertically(spring(stiffness = 350f)) { it / 3 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 4 } + fadeOut())
        }, label = "storeReader") { snapshot ->
        val app = snapshot.selected
        val fdroid = snapshot.selectedFdroid
        if (app == null && fdroid == null) return@AnimatedContent
        val identity = app?.repository?.fullName ?: fdroid!!.packageName
        val name = app?.repository?.name ?: fdroid!!.name
        val projectUrl = fdroid?.projectUrl
        val openProject: (() -> Unit)? = when {
            app != null -> ({ onOpenRepository(app.repository) })
            projectUrl?.startsWith("https://") == true -> ({ onOpenExternal(projectUrl) })
            else -> null
        }
        val list = if (fdroid != null) snapshot.filteredFdroidApps.map { it.packageName }
            else (snapshot.filteredSources + snapshot.filteredApps).distinctBy { it.fullName }.map { it.fullName }
        val index = list.indexOf(identity)
        val hasNext = index >= 0 && index < list.lastIndex
        val lazyState = rememberLazyListState()
        val threshold = with(LocalDensity.current) { 96.dp.toPx() }
        val haptics = LocalHapticFeedback.current
        var pull by remember(identity) { mutableFloatStateOf(0f) }
        var horizontal by remember(identity) { mutableFloatStateOf(0f) }
        val next by rememberUpdatedState({ onAction(StoreAction.NextApp) })
        val currentProject by rememberUpdatedState(openProject)
        val connection = remember(identity, hasNext, threshold) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (pull > 0f && available.y > 0f) {
                        val used = minOf(pull, available.y)
                        pull -= used
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (hasNext && !lazyState.canScrollForward && available.y < 0 && source == NestedScrollSource.UserInput) {
                        val before = pull
                        pull = (pull - available.y).coerceAtMost(threshold * 1.5f)
                        if (before < threshold && pull >= threshold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    val advance = pull >= threshold
                    pull = 0f
                    if (advance) next()
                    return if (advance) available else Velocity.Zero
                }
            }
        }
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.store_app_title)) },
                    navigationIcon = { IconButton(onClick = { onAction(StoreAction.CloseApp) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.github_cancel))
                    } },
                    actions = { if (openProject != null) IconButton(onClick = openProject) {
                        Icon(Icons.Default.Code, stringResource(R.string.github_store_open_details))
                    } })
            }
        ) { padding ->
            LazyColumn(state = lazyState,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .nestedScroll(connection)
                    .pointerInput(identity, openProject != null) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontal = 0f },
                            onHorizontalDrag = { change, amount ->
                                if (currentProject != null) { horizontal = (horizontal + amount).coerceAtLeast(0f); change.consume() }
                            },
                            onDragEnd = { if (horizontal >= threshold) currentProject?.invoke(); horizontal = 0f },
                            onDragCancel = { horizontal = 0f }
                        )
                    }.graphicsLayer { translationY = -pull / 4; translationX = horizontal / 4 },
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item("summary") {
                    Surface(shape = GithubExpressiveShapes.container, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (fdroid?.iconUrl != null) {
                                takagi.ru.monica.github.component.GithubAvatar(
                                    login = name, avatarUrl = fdroid.iconUrl, size = 80.dp, shape = GithubExpressiveShapes.control)
                            } else Surface(shape = GithubExpressiveShapes.control, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Default.Android, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(app?.repository?.fullName ?: fdroid!!.sourceName, style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text(app?.latestRelease?.tagName ?: fdroid?.latestVersionName.orEmpty())
                                val size = app?.apkAssets?.firstOrNull()?.sizeBytes ?: fdroid?.sizeBytes
                                if (size != null) Text(formatBytes(size))
                            }
                            if (app?.latestRelease?.isPrerelease == true) Text(stringResource(R.string.github_release_prerelease))
                            snapshot.installedVersion?.let { Text(stringResource(R.string.github_store_installed, it)) }
                        }
                    }
                }
                if (snapshot.isLoadingDetail) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (snapshot.detailError) item("error") {
                    TextButton(onClick = { onAction(StoreAction.RetryDetail) }) { Text(stringResource(R.string.github_retry)) }
                }
                if (app != null) {
                    items(app.apkAssets.toApkPackages(), key = { it.asset.id }) { apk ->
                        ApkAssetRow(apk, snapshot.downloads[apk.asset.id.toString()], onDownload = {
                            onAction(StoreAction.DownloadApk(apk.asset.id.toString(), apk.asset.downloadUrl, apk.asset.name, identity))
                        }, onInstallApk = onInstallApk)
                    }
                    if (!snapshot.isLoadingDetail && !snapshot.detailError && app.apkAssets.isEmpty()) item("unavailable") {
                        Text(stringResource(R.string.github_store_no_apk))
                    }
                } else if (fdroid?.apkUrl != null) item("install") {
                    val key = fdroid.packageName
                    val download = { onAction(StoreAction.DownloadApk(key, fdroid.apkUrl, fdroid.apkName ?: "$key.apk", key, fdroid.sha256)) }
                    val progress = snapshot.downloads[key]
                    if (progress == null) Button(onClick = download, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.github_store_download))
                    } else ApkDownloadControls(progress, download, onInstallApk)
                }
                item("description") {
                    val description = app?.repository?.description ?: fdroid?.description ?: fdroid?.summary.orEmpty()
                    ExpandableStoreText(description, identity + "description")
                }
                if (!app?.latestRelease?.body.isNullOrBlank()) item("notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.store_release_notes), style = MaterialTheme.typography.titleMedium)
                        ExpandableStoreText(app!!.latestRelease!!.body!!, identity + "notes")
                    }
                }
                if (openProject != null) item("project") {
                    OutlinedButton(onClick = openProject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.store_swipe_project))
                    }
                }
                item("next") {
                    if (hasNext) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (pull >= threshold) R.string.store_release_next else R.string.store_pull_next),
                                style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onAction(StoreAction.NextApp) }) { Text(stringResource(R.string.store_next)) }
                        }
                    } else Text(stringResource(R.string.store_end), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExpandableStoreText(text: String, key: String) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var overflowing by remember(key) { mutableStateOf(false) }
    val plain = remember(text) { if (Regex("</?[a-zA-Z][^>]*>").containsMatchIn(text)) androidx.core.text.HtmlCompat.fromHtml(text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else text }
    Column {
        Text(plain, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow })
        if (expanded || overflowing) TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.store_collapse else R.string.store_more))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreSourcesSheet(state: StoreUiState, onAction: (StoreAction) -> Unit, onDismiss: () -> Unit) {
    takagi.ru.monica.github.component.GithubModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            item { Text(stringResource(R.string.store_sources), style = MaterialTheme.typography.titleLarge) }
            items(FdroidSources.builtIn, key = { it.url }) { source ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, style = MaterialTheme.typography.titleSmall)
                        Text(source.url, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = source.url in state.enabledFdroidSources,
                        onCheckedChange = { onAction(StoreAction.ToggleFdroidSource(source.url, it)) })
                }
            }
        }
    }
}
