package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun rememberSteamChatGalleryPicker(
    onSelected: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.toString()?.let(onSelected)
    }
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }
}

@Composable
internal fun rememberSteamChatFilePicker(
    onSelected: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let(onSelected)
    }
    return remember(launcher) {
        { launcher.launch(STEAM_CHAT_ATTACHMENT_MIME_TYPES) }
    }
}

@Composable
internal fun SteamChatAttachmentPickerPanel(
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamChatAttachmentChoice(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.steam_chat_attachment_gallery),
                summary = stringResource(R.string.steam_chat_attachment_gallery_summary),
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f)
            )
            SteamChatAttachmentChoice(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.steam_chat_attachment_file),
                summary = stringResource(R.string.steam_chat_attachment_file_summary),
                onClick = onFileClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SteamChatAttachmentChoice(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val STEAM_CHAT_ATTACHMENT_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/avif",
    "video/webm",
    "video/mpeg",
    "video/mp4",
    "video/ogg",
    "application/zip",
    "application/x-zip-compressed"
)
