package takagi.ru.monica.steam.store.gift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftFailure
import takagi.ru.monica.steam.store.gift.presentation.SteamStoreGiftUiState
import takagi.ru.monica.ui.components.MonicaModalBottomSheet
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreGiftRecipientSheet(
    state: SteamStoreGiftUiState,
    onSelect: (SteamFriend) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable(state.pendingItem?.appId) { mutableStateOf("") }
    val selectedSteamId = state.pendingItem?.giftRecipient?.steamId
    val normalizedQuery = query.trim()
    val friends = sortSteamFriendsForList(state.friends).filter { friend ->
        normalizedQuery.isBlank() ||
            friend.displayName.contains(normalizedQuery, ignoreCase = true) ||
            friend.realName.contains(normalizedQuery, ignoreCase = true) ||
            friend.steamId.contains(normalizedQuery, ignoreCase = true)
    }

    MonicaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_store_gift_choose_friend),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = GoogleSansFlexFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = state.pendingItem?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.loading && !state.refreshing) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.steam_store_gift_refresh)
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.steam_store_gift_search_hint)) },
                shape = RoundedCornerShape(18.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.steam_store_gift_validation_note),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
            if (state.fromCache) {
                Text(
                    text = stringResource(R.string.steam_store_gift_cached),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (state.failure != null && state.friends.isNotEmpty()) {
                SteamStoreGiftFailureBanner(failure = state.failure, onRetry = onRefresh)
            }

            when {
                state.loading && state.friends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                state.failure != null && state.friends.isEmpty() -> SteamStoreGiftFailureState(
                    failure = state.failure,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize()
                )
                friends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.steam_store_gift_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(friends, key = SteamFriend::steamId) { friend ->
                        SteamStoreGiftFriendRow(
                            friend = friend,
                            selected = friend.steamId == selectedSteamId,
                            onClick = { onSelect(friend) }
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun SteamStoreGiftFriendRow(
    friend: SteamFriend,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FriendAvatar(friend = friend, size = 48)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildList {
                        add(friend.personaState.label())
                        friend.countryCode.takeIf(String::isNotBlank)?.let(::add)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SteamStoreGiftFailureBanner(
    failure: SteamStoreGiftFailure,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(
                text = giftFailureMessage(failure),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.steam_store_retry))
            }
        }
    }
}

@Composable
private fun SteamStoreGiftFailureState(
    failure: SteamStoreGiftFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = giftFailureMessage(failure),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.steam_store_retry))
        }
    }
}

@Composable
private fun giftFailureMessage(failure: SteamStoreGiftFailure): String = stringResource(
    when (failure) {
        SteamStoreGiftFailure.ACCOUNT_REQUIRED -> R.string.steam_store_gift_account_required
        SteamStoreGiftFailure.SESSION_REQUIRED -> R.string.steam_store_gift_session_required
        SteamStoreGiftFailure.NETWORK -> R.string.steam_store_gift_network_error
        SteamStoreGiftFailure.INVALID_RECIPIENT -> R.string.steam_store_gift_invalid_recipient
        SteamStoreGiftFailure.UNAVAILABLE -> R.string.steam_store_gift_unavailable
    }
)
