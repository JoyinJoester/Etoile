package takagi.ru.monica.steam.store.gift.domain

import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship

internal fun SteamFriend.toSteamStoreGiftRecipient(): SteamStoreGiftRecipient? {
    if (relationship != SteamFriendRelationship.FRIEND) return null
    val accountId = steamStoreAccountIdFromSteamId64(steamId) ?: return null
    return SteamStoreGiftRecipient(
        steamId = steamId,
        accountId = accountId,
        displayName = displayName,
        avatarUrl = avatarUrl,
        countryCode = countryCode
    )
}
