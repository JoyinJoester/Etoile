package takagi.ru.monica.debug

import androidx.compose.runtime.*
import androidx.compose.material3.*
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.store.*

@Composable
internal fun StoreReaderSample() {
    val apps = remember { listOf(
        FdroidApp("sample.reader", "Capy Reader", "A compact feed reader", "1.0", "reader.apk", "https://example.invalid/reader.apk", 23000000,
            projectUrl = "https://github.com/jocmp/capyreader", description = "A feed reader with a comfortable reading experience.\n".repeat(20)),
        FdroidApp("sample.next", "Next Application", "Next application in the current catalog", "2.0", "next.apk", "https://example.invalid/next.apk", 12000000,
            projectUrl = "https://github.com/sample/next", description = "Short description.")
    ) }
    var state by remember { mutableStateOf(StoreUiState(isLoading = false, fdroidApps = apps, selectedFdroid = apps[0])) }
    var project by remember { mutableStateOf<String?>(null) }
    if (project != null) AlertDialog(onDismissRequest = { project = null }, title = { Text("Project opened") },
        text = { Text(project!!) }, confirmButton = { TextButton(onClick = { project = null }) { Text("Close") } })
    StoreScreen(state, onAction = { action -> state = when(action) {
        StoreAction.NextApp -> state.copy(selectedFdroid = apps[1])
        StoreAction.CloseApp -> state.copy(selectedFdroid = null)
        is StoreAction.OpenFdroidApp -> state.copy(selectedFdroid = action.app)
        is StoreAction.ToggleFdroidSource -> state.copy(enabledFdroidSources = if(action.enabled) state.enabledFdroidSources + action.url else state.enabledFdroidSources - action.url)
        is StoreAction.DownloadApk -> state.copy(downloads = state.downloads + (action.key to ApkDownloadUiState(progress = 0.5f)))
        else -> state
    } }, onOpenRepository = {}, onOpenExternal = { project = it }, onInstallApk = {})
}
