package takagi.ru.monica.steam.friends.chat.presentation

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

/** Sends a chat message and performs one bounded recovery for an expired session. */
internal suspend fun sendSteamChatMessageWithSessionRecovery(
    gateway: SteamChatGateway,
    account: SteamAccount,
    partnerSteamId: String,
    pending: SteamChatMessage,
    sessionResolver: SteamAccountSessionResolver?,
    onSessionRefreshed: suspend (SteamAccount) -> Unit = {}
): Result<SteamChatMessage> {
    val preparedAccount = resolveSteamChatSession(account, sessionResolver)
    notifySessionChanged(account, preparedAccount, onSessionRefreshed)
    val firstAttempt = runSteamChatCatching {
        gateway.sendMessage(
            account = preparedAccount,
            partnerSteamId = partnerSteamId,
            body = pending.body,
            clientMessageId = pending.clientMessageId
        )
    }
    val firstError = firstAttempt.exceptionOrNull()
    if (firstError == null || !firstError.requiresSteamChatSessionRefresh()) return firstAttempt

    logSteamChatFailure("send_session_refresh", firstError)
    val refreshedAccount = runSteamChatCatching {
        resolveSteamChatSession(account, sessionResolver, forceRefresh = true)
    }.getOrNull() ?: return firstAttempt
    notifySessionChanged(account, refreshedAccount, onSessionRefreshed)

    return runSteamChatCatching {
        gateway.sendMessage(
            account = refreshedAccount,
            partnerSteamId = partnerSteamId,
            body = pending.body,
            clientMessageId = pending.clientMessageId
        )
    }
}

private suspend fun notifySessionChanged(
    previous: SteamAccount,
    current: SteamAccount,
    onSessionRefreshed: suspend (SteamAccount) -> Unit
) {
    if (previous.accessToken == current.accessToken &&
        previous.refreshToken == current.refreshToken &&
        previous.steamLoginSecure == current.steamLoginSecure
    ) {
        return
    }
    runSteamChatCatching { onSessionRefreshed(current) }
        .onFailure { logSteamChatFailure("session_resolved", it) }
}
