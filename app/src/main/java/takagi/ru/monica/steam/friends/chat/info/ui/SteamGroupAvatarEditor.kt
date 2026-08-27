package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary

/** Keeps the edit affordance outside the rounded group avatar clipping boundary. */
@Composable
internal fun SteamGroupAvatarEditor(
    group: SteamGroupChatSummary,
    members: List<SteamFriend>,
    canEdit: Boolean,
    updating: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(84.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(72.dp)
                .clip(RoundedCornerShape(22))
                .then(if (canEdit && !updating) Modifier.clickable(onClick = onPick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            SteamGroupAvatarImage(
                url = group.avatarUrl,
                members = members,
                contentDescription = group.name,
                modifier = Modifier.size(72.dp)
            )
            if (updating) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
        if (canEdit && !updating) {
            IconButton(
                onClick = onPick,
                modifier = Modifier.align(Alignment.BottomEnd).size(48.dp)
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change group avatar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
