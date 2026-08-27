package takagi.ru.monica.steam.foundation.media

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

internal data class SteamImageViewerStrings(
    @StringRes val imageDescription: Int,
    @StringRes val close: Int,
    @StringRes val previous: Int,
    @StringRes val next: Int,
    @StringRes val position: Int,
    @StringRes val download: Int,
    @StringRes val downloading: Int,
    @StringRes val downloadSuccess: Int,
    @StringRes val downloadFailed: Int,
    @StringRes val downloadUnsupported: Int,
    @StringRes val downloadTooLarge: Int,
    @StringRes val permissionDenied: Int,
    @StringRes val invalidSource: Int,
    @StringRes val loadFailed: Int
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamFullscreenImageViewer(
    title: String,
    images: List<String>,
    initialIndex: Int,
    fileStemForIndex: (Int) -> String,
    strings: SteamImageViewerStrings,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember(context) {
        SteamImageDownloader(context.applicationContext)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(images.indices)
    ) { images.size }
    var downloading by remember { mutableStateOf(false) }
    var pendingPermissionIndex by remember { mutableStateOf<Int?>(null) }

    val startDownload: (Int) -> Unit = { requestedIndex ->
        if (!downloading) {
            val safeIndex = requestedIndex.coerceIn(images.indices)
            downloading = true
            scope.launch {
                val result = downloader.download(
                    imageUrl = images[safeIndex],
                    fileStem = fileStemForIndex(safeIndex)
                )
                val message = when (result) {
                    is SteamImageDownloadResult.Success -> context.getString(
                        strings.downloadSuccess,
                        result.displayName
                    )
                    SteamImageDownloadResult.PermissionRequired -> context.getString(
                        strings.permissionDenied
                    )
                    SteamImageDownloadResult.InvalidSource -> context.getString(
                        strings.invalidSource
                    )
                    SteamImageDownloadResult.UnsupportedImage -> context.getString(
                        strings.downloadUnsupported
                    )
                    SteamImageDownloadResult.TooLarge -> context.getString(
                        strings.downloadTooLarge
                    )
                    SteamImageDownloadResult.NetworkFailure,
                    SteamImageDownloadResult.StorageFailure -> context.getString(
                        strings.downloadFailed
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                downloading = false
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val index = pendingPermissionIndex
        pendingPermissionIndex = null
        if (granted && index != null) {
            startDownload(index)
        } else {
            Toast.makeText(context, strings.permissionDenied, Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                SteamFullscreenImagePage(
                    url = images[page],
                    contentDescription = stringResource(strings.imageDescription, page + 1),
                    loadFailedText = stringResource(strings.loadFailed)
                )
            }

            ImageViewerTopControls(
                title = title,
                downloading = downloading,
                closeDescription = stringResource(strings.close),
                downloadDescription = stringResource(strings.download),
                downloadingDescription = stringResource(strings.downloading),
                onDismiss = onDismiss,
                onDownload = {
                    val currentIndex = pagerState.currentPage
                    if (downloader.requiresLegacyStoragePermission()) {
                        pendingPermissionIndex = currentIndex
                        storagePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    } else {
                        startDownload(currentIndex)
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (images.size > 1) {
                ImageViewerPageControls(
                    currentIndex = pagerState.currentPage,
                    imageCount = images.size,
                    positionText = stringResource(
                        strings.position,
                        pagerState.currentPage + 1,
                        images.size
                    ),
                    previousDescription = stringResource(strings.previous),
                    nextDescription = stringResource(strings.next),
                    onPrevious = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage - 1).coerceAtLeast(0)
                            )
                        }
                    },
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(images.lastIndex)
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
