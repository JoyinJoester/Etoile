package takagi.ru.monica.steam.network.optimization.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R

internal class SteamHostsDataExchange(
    val importFromFile: () -> Unit,
    val exportToFile: () -> Unit,
    val copyToClipboard: () -> Unit,
    val pasteFromClipboard: () -> Unit
)

@Composable
internal fun rememberSteamHostsDataExchange(
    currentText: String,
    onDraftImported: (String) -> Unit,
    onMessage: (String) -> Unit
): SteamHostsDataExchange {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val latestText by rememberUpdatedState(currentText)
    val latestOnDraftImported by rememberUpdatedState(onDraftImported)
    val latestOnMessage by rememberUpdatedState(onMessage)
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = pendingExportText
        pendingExportText = null
        if (uri != null && text != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            output.writer(Charsets.UTF_8).use { writer -> writer.write(text) }
                        } ?: throw IOException("Cannot open export destination")
                    }
                }
                latestOnMessage(
                    context.getString(
                        if (result.isSuccess) {
                            R.string.steam_network_optimization_exported
                        } else {
                            R.string.steam_network_optimization_export_failed
                        }
                    )
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytesLimited(MAX_HOSTS_FILE_BYTES)
                        } ?: throw IOException("Cannot open imported file")
                        normalizeImportedText(bytes.toString(Charsets.UTF_8))
                    }
                }
                result.onSuccess { importedText ->
                    latestOnDraftImported(importedText)
                    latestOnMessage(
                        context.getString(R.string.steam_network_optimization_imported_to_draft)
                    )
                }.onFailure {
                    latestOnMessage(
                        context.getString(R.string.steam_network_optimization_import_failed)
                    )
                }
            }
        }
    }

    return remember(importLauncher, exportLauncher, clipboardManager) {
        SteamHostsDataExchange(
            importFromFile = {
                importLauncher.launch(
                    arrayOf("text/plain", "text/*", "application/octet-stream")
                )
            },
            exportToFile = {
                pendingExportText = latestText
                exportLauncher.launch(DEFAULT_EXPORT_FILE_NAME)
            },
            copyToClipboard = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(CLIPBOARD_LABEL, latestText)
                )
                latestOnMessage(
                    context.getString(R.string.steam_network_optimization_copied)
                )
            },
            pasteFromClipboard = {
                val pastedText = clipboardManager.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                when {
                    pastedText.isNullOrEmpty() -> latestOnMessage(
                        context.getString(R.string.steam_network_optimization_clipboard_empty)
                    )
                    pastedText.toByteArray(Charsets.UTF_8).size > MAX_HOSTS_FILE_BYTES ->
                        latestOnMessage(
                            context.getString(R.string.steam_network_optimization_clipboard_too_large)
                        )
                    else -> {
                        latestOnDraftImported(normalizeImportedText(pastedText))
                        latestOnMessage(
                            context.getString(R.string.steam_network_optimization_pasted_to_draft)
                        )
                    }
                }
            }
        )
    }
}

private fun normalizeImportedText(text: String): String {
    require('\u0000' !in text) { "Binary data is not supported" }
    return text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
}

private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Hosts file is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_HOSTS_FILE_BYTES = 512 * 1024
private const val DEFAULT_EXPORT_FILE_NAME = "etoile-hosts.txt"
private const val CLIPBOARD_LABEL = "Etoile Hosts"
