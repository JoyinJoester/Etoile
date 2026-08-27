package takagi.ru.monica.steam.friends.chat.background.domain

import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin
import takagi.ru.monica.steam.data.SteamStorageSource

data class SteamChatNotificationRequest(
    val origin: SteamAccountSessionOrigin,
    val accountId: Long,
    val accountSteamId: String,
    val partnerSteamId: String? = null
) {
    val isValid: Boolean
        get() = accountId != 0L &&
            accountSteamId.isSteamChatSteamId() &&
            (partnerSteamId == null || partnerSteamId.isSteamChatSteamId()) &&
            when (val source = origin.source) {
                SteamStorageSource.Local -> true
                is SteamStorageSource.Mdbx ->
                    source.databaseId > 0L && !origin.entryId.isNullOrBlank()
            }
}

internal fun String.isSteamChatSteamId(): Boolean = matches(STEAM_ID_PATTERN)

private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
