package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.media.SteamFullscreenImageViewer
import takagi.ru.monica.steam.foundation.media.SteamImageDownloadPolicy
import takagi.ru.monica.steam.foundation.media.SteamImageViewerStrings
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent

@Composable
internal fun AttachmentContent(
    content: SteamChatRichContent.Attachment,
    modifier: Modifier
) {
    val context = LocalContext.current
    var revealed by remember(content.url, content.spoiler) {
        mutableStateOf(!content.spoiler)
    }
    var showImageViewer by remember(content.url) { mutableStateOf(false) }
    val open = {
        if (content.kind == SteamChatAttachmentKind.IMAGE &&
            SteamImageDownloadPolicy.isAllowedUrl(content.url)
        ) {
            showImageViewer = true
        } else {
            runCatching {
                val uri = Uri.parse(content.url)
                if (uri.scheme == "https") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
        Unit
    }

    Column(
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Crossfade(
            targetState = revealed,
            animationSpec = tween(durationMillis = 220),
            label = "steam-chat-spoiler-image"
        ) { isRevealed ->
            if (!isRevealed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        .clickable { revealed = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.steam_chat_spoiler_reveal),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (content.kind == SteamChatAttachmentKind.IMAGE) {
                        SteamChatRemoteImage(
                            url = content.url,
                            contentDescription = content.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = open)
                        )
                    }
                    if (content.kind != SteamChatAttachmentKind.IMAGE) {
                        Row(
                            modifier = Modifier.clickable(onClick = open),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (content.kind) {
                                    SteamChatAttachmentKind.VIDEO -> Icons.Default.Movie
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null
                            )
                            Text(
                                text = content.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImageViewer) {
        SteamFullscreenImageViewer(
            title = content.label,
            images = listOf(content.url),
            initialIndex = 0,
            fileStemForIndex = {
                SteamImageDownloadPolicy.safeFileStem(
                    rawName = "steam_chat_${content.label.substringBeforeLast('.', content.label)}",
                    fallbackStem = "steam_chat_image"
                )
            },
            strings = chatImageViewerStrings,
            onDismiss = { showImageViewer = false }
        )
    }
}

private val chatImageViewerStrings = SteamImageViewerStrings(
    imageDescription = R.string.steam_chat_image_description,
    close = R.string.steam_chat_image_viewer_close,
    previous = R.string.steam_chat_image_viewer_previous,
    next = R.string.steam_chat_image_viewer_next,
    position = R.string.steam_chat_image_viewer_position,
    download = R.string.steam_chat_image_download,
    downloading = R.string.steam_chat_image_downloading,
    downloadSuccess = R.string.steam_chat_image_download_success,
    downloadFailed = R.string.steam_chat_image_download_failed,
    downloadUnsupported = R.string.steam_chat_image_download_unsupported,
    downloadTooLarge = R.string.steam_chat_image_download_too_large,
    permissionDenied = R.string.steam_chat_image_permission_denied,
    invalidSource = R.string.steam_chat_image_invalid_source,
    loadFailed = R.string.steam_chat_image_load_failed
)
