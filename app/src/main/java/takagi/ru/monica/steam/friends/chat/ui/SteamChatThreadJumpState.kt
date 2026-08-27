package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatJumpMessage
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestUiState
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatReadingUiState
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatJumpToLatestState
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState

@Composable
internal fun rememberDirectSteamChatJumpToLatestState(
    state: SteamChatUiState,
    messages: List<SteamChatMessage>,
    conversationKey: String,
    readingUi: SteamChatReadingUiState
): SteamChatJumpToLatestUiState {
    val partnerSteamId = state.selectedPartnerSteamId.orEmpty()
    val initialAcknowledgedTimestamp = state.sessions?.sessions
        ?.firstOrNull { it.partnerSteamId == partnerSteamId }
        ?.lastViewTimestamp
        ?: 0L
    val jumpMessages = remember(messages, state.accountSteamId) {
        messages.map { message ->
            SteamChatJumpMessage(
                id = message.stableId,
                timestamp = message.timestamp,
                incoming = !message.isOutgoing(state.accountSteamId)
            )
        }
    }
    return rememberSteamChatJumpToLatestState(
        conversationKey = conversationKey,
        initialAcknowledgedTimestamp = initialAcknowledgedTimestamp,
        messages = jumpMessages,
        lastVisibleMessageId = readingUi.lastVisibleMessageId,
        messagesBelow = readingUi.messagesBelow,
        restored = readingUi.restored
    )
}
