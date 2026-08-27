package takagi.ru.monica.steam.friends.chat.gameinvite.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.gameinvite.data.SteamChatGameInviteMetadataRepository
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.SteamChatGameInviteMetadata
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.toGameInvitePresentation
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage

@Composable
internal fun SteamChatGameInviteCard(
    content: SteamChatRichContent.GameInvite,
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val language = remember(locale.language) {
        if (locale.language.equals("zh", ignoreCase = true)) "schinese" else "english"
    }
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SteamChatGameInviteMetadataRepository.get(context.applicationContext)
    }
    val metadata by produceState<SteamChatGameInviteMetadata?>(
        initialValue = null,
        key1 = content.appId,
        key2 = language
    ) {
        value = content.appId?.let { repository.resolve(it, language) }
    }
    val presentation = remember(content, metadata) {
        content.toGameInvitePresentation(metadata)
    }
    val title = presentation.gameName
        ?: presentation.appId?.let { stringResource(R.string.steam_chat_game_invite_app_id, it) }
        ?: stringResource(R.string.steam_chat_notification_game_invite)
    val openStore = {
        presentation.appId?.let(onOpenStoreApp)
        Unit
    }

    Surface(
        modifier = modifier
            .widthIn(min = 232.dp, max = 304.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(width = 112.dp, height = 63.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        presentation.artworkUrl?.let { artworkUrl ->
                            SteamChatRemoteImage(
                                url = artworkUrl,
                                contentDescription = stringResource(
                                    R.string.steam_chat_game_invite_artwork,
                                    title
                                ),
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                fallbackIcon = Icons.Default.SportsEsports
                            )
                        } ?: Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.steam_notification_kind_game_invite),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    presentation.appId?.let { appId ->
                        Text(
                            text = stringResource(R.string.steam_chat_game_invite_app_id, appId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (presentation.appId != null) {
                FilledTonalButton(
                    onClick = openStore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.steam_chat_game_invite_view_store),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
