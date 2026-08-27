package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.sortSteamFriendsForList
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.label
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance

@Composable
internal fun SteamChatFriendPicker(
    friends: List<SteamFriend>,
    loading: Boolean,
    query: String,
    onOpenThread: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockContentClearance = LocalSteamDockContentClearance.current
    val normalizedQuery = query.trim()
    val orderedFriends = sortSteamChatFriendsForPicker(friends)
    val visibleFriends = orderedFriends.filter { friend ->
        normalizedQuery.isBlank() ||
            friend.displayName.contains(normalizedQuery, ignoreCase = true) ||
            friend.realName.contains(normalizedQuery, ignoreCase = true) ||
            friend.steamId.contains(normalizedQuery, ignoreCase = true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            visibleFriends.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 18.dp,
                            top = 10.dp,
                            end = 6.dp,
                            bottom = 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Text(
                            text = stringResource(R.string.steam_friends_empty),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = dockContentClearance + 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleFriends, key = SteamFriend::steamId) { friend ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clickable { onOpenThread(friend.steamId) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FriendAvatar(friend = friend, size = 50)
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = friend.displayName.ifBlank { friend.steamId },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = friend.personaState.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddFriend,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .steamDockActionClearance(extraBottomSpacing = 12.dp)
                .padding(end = 18.dp)
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = stringResource(R.string.steam_friend_add_title)
            )
        }
    }
}

internal fun sortSteamChatFriendsForPicker(friends: List<SteamFriend>): List<SteamFriend> =
    sortSteamFriendsForList(friends)
