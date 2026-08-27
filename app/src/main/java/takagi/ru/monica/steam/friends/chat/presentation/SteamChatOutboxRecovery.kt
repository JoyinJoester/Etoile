package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamChatRecoveredOutbox

internal suspend fun recoverPendingSteamChatOutbox(
    outbox: SteamChatOutbox?,
    account: SteamAccount,
    partnerSteamId: String,
    accountKey: String = "${account.id}:${account.steamId}",
    ioDispatcher: CoroutineDispatcher,
    isCurrent: () -> Boolean,
    onRecovered: (SteamChatRecoveredOutbox) -> Unit
) {
    val recovered = runSteamChatCatching {
        withContext(ioDispatcher) { outbox?.recover(account, partnerSteamId, accountKey).orEmpty() }
    }.onFailure { logSteamChatFailure("outbox_recover", it) }.getOrDefault(emptyList())
    if (!isCurrent()) return
    recovered.forEach(onRecovered)
}
