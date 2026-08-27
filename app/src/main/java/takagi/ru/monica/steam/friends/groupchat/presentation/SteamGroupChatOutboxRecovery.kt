package takagi.ru.monica.steam.friends.groupchat.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatRecoveredOutbox

internal suspend fun recoverPendingSteamGroupChatOutbox(
    outbox: SteamGroupChatOutbox?,
    account: SteamAccount,
    groupId: String,
    chatId: String,
    accountKey: String,
    ioDispatcher: CoroutineDispatcher,
    isCurrent: () -> Boolean,
    onRecovered: (SteamGroupChatRecoveredOutbox) -> Unit
) {
    val recovered = runGroupChatCatching {
        withContext(ioDispatcher) {
            outbox?.recover(account, groupId, chatId, accountKey).orEmpty()
        }
    }.onFailure { logGroupChatSendFailure("outbox_recover", it) }
        .getOrDefault(emptyList())
    if (!isCurrent()) return
    recovered.forEach(onRecovered)
}
