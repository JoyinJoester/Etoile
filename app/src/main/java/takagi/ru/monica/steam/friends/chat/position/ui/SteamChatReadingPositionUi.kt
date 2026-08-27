package takagi.ru.monica.steam.friends.chat.position.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import takagi.ru.monica.steam.friends.chat.position.data.SteamChatReadingPositionStore
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingPosition
import takagi.ru.monica.steam.friends.chat.position.domain.resolveSteamChatReadingIndex
import takagi.ru.monica.steam.friends.chat.position.domain.steamChatMessagesBelow

internal data class SteamChatReadingUiState(
    val restored: Boolean,
    val messagesBelow: Int,
    val lastVisibleMessageId: String?
)

@Composable
internal fun rememberSteamChatReadingPosition(
    conversationKey: String,
    messageIds: List<String>,
    requestedMessageId: String?,
    leadingItemCount: Int,
    listState: LazyListState
): State<SteamChatReadingUiState> {
    val context = LocalContext.current
    val store = remember(context) { SteamChatReadingPositionStore(context) }
    var restored by remember(conversationKey) { mutableStateOf(false) }
    var handledRequestedMessageId by remember(conversationKey) { mutableStateOf<String?>(null) }
    val currentMessageIds by rememberUpdatedState(messageIds)
    val messageIdSet = remember(messageIds) { messageIds.toSet() }
    val lastVisibleMessageId by remember(listState, messageIdSet) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull { it.key.toString() in messageIdSet }
                ?.key
                ?.toString()
        }
    }
    val messagesBelow by remember(messageIds, lastVisibleMessageId) {
        derivedStateOf { steamChatMessagesBelow(messageIds, lastVisibleMessageId) }
    }

    LaunchedEffect(conversationKey, messageIds, requestedMessageId, leadingItemCount) {
        if (restored || messageIds.isEmpty()) return@LaunchedEffect
        val saved = store.load(conversationKey)
        val index = resolveSteamChatReadingIndex(
            messageIds = messageIds,
            requestedMessageId = requestedMessageId,
            savedMessageId = saved?.messageId
        )
        if (index >= 0) {
            val restoresSavedOffset = requestedMessageId == null &&
                saved?.messageId == messageIds[index]
            listState.scrollToItem(
                index = index + leadingItemCount,
                scrollOffset = if (restoresSavedOffset) saved?.scrollOffset ?: 0 else 0
            )
            if (messageIds[index] == requestedMessageId) {
                handledRequestedMessageId = requestedMessageId
            }
        }
        restored = true
    }

    LaunchedEffect(
        conversationKey,
        requestedMessageId,
        messageIds,
        leadingItemCount,
        restored,
        handledRequestedMessageId
    ) {
        val requested = requestedMessageId ?: return@LaunchedEffect
        if (!restored || handledRequestedMessageId == requested) return@LaunchedEffect
        val index = messageIds.indexOf(requested)
        if (index >= 0) {
            listState.scrollToItem(index + leadingItemCount)
            handledRequestedMessageId = requested
        }
    }

    LaunchedEffect(conversationKey, restored, listState) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { visibleReadingPosition(listState, currentMessageIds.toSet()) }
            .distinctUntilChanged()
            .collectLatest { position ->
                if (position == null) return@collectLatest
                delay(250)
                store.save(conversationKey, position)
            }
    }

    DisposableEffect(conversationKey, listState) {
        onDispose {
            visibleReadingPosition(listState, currentMessageIds.toSet())?.let { position ->
                store.save(conversationKey, position)
            }
        }
    }

    return remember(restored, messagesBelow, lastVisibleMessageId) {
        mutableStateOf(
            SteamChatReadingUiState(
                restored = restored,
                messagesBelow = messagesBelow,
                lastVisibleMessageId = lastVisibleMessageId
            )
        )
    }
}

internal suspend fun LazyListState.animateToLatestSteamChatMessage(
    messageCount: Int,
    leadingItemCount: Int
) {
    if (messageCount <= 0) return
    val lastLaidOutIndex = layoutInfo.totalItemsCount - 1
    if (lastLaidOutIndex < 0) return
    val target = minOf(messageCount - 1 + leadingItemCount, lastLaidOutIndex)
    try {
        animateScrollToItem(target)
    } catch (_: IndexOutOfBoundsException) {
        // A history refresh can replace the lazy-list snapshot during animation.
    } catch (_: IllegalArgumentException) {
        // The next list snapshot will expose a valid target.
    }
}

internal suspend fun LazyListState.scrollToLatestSteamChatMessage(
    messageCount: Int,
    leadingItemCount: Int
) {
    if (messageCount <= 0) return
    val lastLaidOutIndex = layoutInfo.totalItemsCount - 1
    if (lastLaidOutIndex < 0) return
    val target = minOf(messageCount - 1 + leadingItemCount, lastLaidOutIndex)
    try {
        scrollToItem(target)
    } catch (_: IndexOutOfBoundsException) {
        // Insets and message snapshots can settle in adjacent frames.
    } catch (_: IllegalArgumentException) {
        // The following layout frame will expose a valid target.
    }
}

@Composable
internal fun SteamChatAutoScrollToLatestEffect(
    conversationKey: String,
    latestMessageId: String?,
    latestMessageIsOutgoing: Boolean,
    messageCount: Int,
    leadingItemCount: Int,
    messagesBelow: Int,
    restored: Boolean,
    listState: LazyListState
) {
    SteamChatImeBottomAnchorEffect(
        conversationKey = conversationKey,
        messageCount = messageCount,
        leadingItemCount = leadingItemCount,
        messagesBelow = messagesBelow,
        restored = restored,
        listState = listState
    )
    var observedLatestMessageId by remember(conversationKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(conversationKey, latestMessageId, restored) {
        val latest = latestMessageId ?: return@LaunchedEffect
        if (!restored) return@LaunchedEffect
        val previousLatestId = observedLatestMessageId
        observedLatestMessageId = latest
        if (previousLatestId == null || previousLatestId == latest) return@LaunchedEffect
        if (latestMessageIsOutgoing || messagesBelow <= 2) {
            withFrameNanos { }
            listState.animateToLatestSteamChatMessage(messageCount, leadingItemCount)
        }
    }
}

private fun visibleReadingPosition(
    listState: LazyListState,
    messageIds: Set<String>
): SteamChatReadingPosition? {
    val item = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.key.toString() in messageIds }
        ?: return null
    return SteamChatReadingPosition(
        messageId = item.key.toString(),
        scrollOffset = (-item.offset).coerceAtLeast(0)
    )
}
