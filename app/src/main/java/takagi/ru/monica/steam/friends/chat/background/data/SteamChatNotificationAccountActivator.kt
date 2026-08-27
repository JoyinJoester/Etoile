package takagi.ru.monica.steam.friends.chat.background.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationRequest

suspend fun SteamAccountSourceRepository.activateChatNotificationTarget(
    request: SteamChatNotificationRequest,
    timeoutMillis: Long = 10_000L
): Boolean {
    if (!request.isValid) return false
    selectStorageSource(request.origin.source)
    val sourceState = withTimeoutOrNull(timeoutMillis) {
        state.first { current ->
            current.storageSource != request.origin.source || !current.loading
        }
    } ?: return false
    if (sourceState.storageSource != request.origin.source) return false
    val account = sourceState.accounts.firstOrNull { candidate ->
        candidate.id == request.accountId && candidate.steamId == request.accountSteamId
    } ?: return false
    val handle = sessionHandleForSource(account, sourceState.storageSource) ?: return false
    if (handle.origin != request.origin) return false
    selectAccount(account.id)
    return true
}
