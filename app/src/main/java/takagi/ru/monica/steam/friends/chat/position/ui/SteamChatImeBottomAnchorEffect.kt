package takagi.ru.monica.steam.friends.chat.position.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatImeAnchorState
import takagi.ru.monica.steam.friends.chat.position.domain.reduceSteamChatImeAnchor

@Composable
internal fun SteamChatImeBottomAnchorEffect(
    conversationKey: String,
    messageCount: Int,
    leadingItemCount: Int,
    messagesBelow: Int,
    restored: Boolean,
    listState: LazyListState
) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible = imeInsets.getBottom(density) > 0
    val latestMessagesBelow by rememberUpdatedState(messagesBelow)
    val latestMessageCount by rememberUpdatedState(messageCount)
    val latestLeadingItemCount by rememberUpdatedState(leadingItemCount)
    var anchorState by remember(conversationKey) {
        mutableStateOf(
            SteamChatImeAnchorState(
                imeVisible = imeVisible,
                wasAtBottomBeforeIme = messagesBelow == 0,
                followingIme = imeVisible && restored && messagesBelow == 0,
                restored = restored
            )
        )
    }
    LaunchedEffect(conversationKey, restored, listState, imeInsets, density) {
        snapshotFlow {
            SteamChatImeLayoutSnapshot(
                imeBottomPx = imeInsets.getBottom(density),
                viewportEndOffset = listState.layoutInfo.viewportEndOffset,
                totalItemsCount = listState.layoutInfo.totalItemsCount
            )
        }
            .distinctUntilChanged()
            .collectLatest { layout ->
                val result = reduceSteamChatImeAnchor(
                    previous = anchorState,
                    imeVisible = layout.imeBottomPx > 0,
                    atBottom = latestMessagesBelow == 0,
                    restored = restored,
                    hasMessages = latestMessageCount > 0
                )
                anchorState = result.state
                if (!result.shouldScrollToLatest) return@collectLatest

                // IME and composer resizing do not finish in the first inset
                // frame. Keep a stable, non-animated anchor through the last
                // viewport update, then confirm it again on the following frame.
                withFrameNanos { }
                listState.scrollToLatestSteamChatMessage(
                    latestMessageCount,
                    latestLeadingItemCount
                )
                withFrameNanos { }
                listState.scrollToLatestSteamChatMessage(
                    latestMessageCount,
                    latestLeadingItemCount
                )
            }
    }
}

private data class SteamChatImeLayoutSnapshot(
    val imeBottomPx: Int,
    val viewportEndOffset: Int,
    val totalItemsCount: Int
)
