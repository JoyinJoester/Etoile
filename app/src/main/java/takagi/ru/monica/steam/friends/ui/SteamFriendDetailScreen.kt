package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.token.identity.ui.SteamIdentityInfoCard
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun SteamFriendDetailScreen(
    friend: SteamFriend,
    actionInProgress: Boolean,
    onStartChat: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onChangeRelationship: (SteamFriendRelationshipAction) -> Unit
) {
    val context = LocalContext.current
    val dockContentClearance = LocalSteamDockContentClearance.current
    var pendingAction by remember(friend.steamId) {
        mutableStateOf<SteamFriendRelationshipAction?>(null)
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.steam_friend_confirm_action)) },
            text = {
                Text(stringResource(R.string.steam_friend_confirm_action_summary, friend.displayName))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    onChangeRelationship(action)
                }) { Text(action.label(friend.relationship)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = dockContentClearance + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "friend-detail-hero") { FriendDetailHero(friend) }
        if (friend.isPlaying) {
            item(key = "friend-detail-game") {
                DetailSectionCard(
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                    title = stringResource(R.string.steam_friend_current_game),
                    value = friend.gameName.ifBlank { friend.gameId },
                    emphasized = true
                )
            }
        }
        item(key = "friend-detail-information-title") {
            Text(
                text = stringResource(R.string.steam_friend_profile_information),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item(key = "friend-detail-steamid") {
            SteamIdentityInfoCard(steamId64 = friend.steamId)
        }
        if (friend.friendSince > 0L) {
            item(key = "friend-detail-friends-since") {
                DetailSectionCard(
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    title = stringResource(R.string.steam_friend_friends_since),
                    value = formatSteamFriendTime(friend.friendSince)
                )
            }
        }
        item(key = "friend-detail-last-online") {
            DetailSectionCard(
                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                title = stringResource(R.string.steam_friend_last_online),
                value = if (friend.lastLogoff > 0L) {
                    formatSteamFriendTime(friend.lastLogoff)
                } else {
                    stringResource(R.string.steam_friend_unknown_time)
                }
            )
        }
        if (friend.countryCode.isNotBlank()) {
            item(key = "friend-detail-location") {
                DetailSectionCard(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = stringResource(R.string.steam_friend_location),
                    value = friend.countryCode
                )
            }
        }
        item(key = "friend-detail-chat") {
            FilledTonalButton(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Default.ChatBubble, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.steam_chat_send_message))
            }
        }
        if (onOpenProfile != null) {
            item(key = "friend-detail-game-profile") {
                FilledTonalButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_profile_open_game_profile))
                }
            }
        }
        item(key = "friend-detail-open-profile") {
            FilledTonalButton(
                onClick = { openSteamProfile(context, friend) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.steam_friend_open_profile))
            }
        }
        relationshipActions(friend.relationship).forEach { action ->
            item(key = "friend-detail-action-${action.name}") {
                FilledTonalButton(
                    onClick = { pendingAction = action },
                    enabled = !actionInProgress,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(action.label(friend.relationship))
                }
            }
        }
    }
}

@Composable
private fun SteamFriendRelationshipAction.label(
    relationship: SteamFriendRelationship
): String = stringResource(
    when (this) {
        SteamFriendRelationshipAction.ADD -> R.string.steam_friend_accept
        SteamFriendRelationshipAction.REMOVE -> if (
            relationship == SteamFriendRelationship.REQUEST_OUTGOING
        ) R.string.steam_friend_cancel_request else R.string.steam_friend_remove
        SteamFriendRelationshipAction.BLOCK -> R.string.steam_friend_block
        SteamFriendRelationshipAction.UNBLOCK -> R.string.steam_friend_unblock
    }
)

private fun relationshipActions(
    relationship: SteamFriendRelationship
): List<SteamFriendRelationshipAction> = when (relationship) {
    SteamFriendRelationship.FRIEND -> listOf(
        SteamFriendRelationshipAction.REMOVE,
        SteamFriendRelationshipAction.BLOCK
    )
    SteamFriendRelationship.REQUEST_OUTGOING -> listOf(SteamFriendRelationshipAction.REMOVE)
    SteamFriendRelationship.BLOCKED -> listOf(SteamFriendRelationshipAction.UNBLOCK)
    SteamFriendRelationship.UNKNOWN -> listOf(SteamFriendRelationshipAction.ADD)
    SteamFriendRelationship.REQUEST_INCOMING -> emptyList()
}

@Composable
private fun FriendDetailHero(friend: SteamFriend) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (friend.isPlaying) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FriendAvatar(friend = friend, size = 96)
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GoogleSansFlexFontFamily
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            friend.realName.takeIf(String::isNotBlank)?.let { realName ->
                Text(
                    text = realName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = friend.statusColor().copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = CircleShape,
                        color = friend.statusColor()
                    ) {}
                    Text(friend.personaState.label(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    emphasized: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
