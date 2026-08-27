package takagi.ru.monica.steam.store.gift.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreGiftRecipient(
    val steamId: String,
    val accountId: Long,
    val displayName: String,
    val avatarUrl: String = "",
    val countryCode: String = ""
)

@Serializable
data class SteamStoreCheckoutLine(
    val packageId: Int,
    val gifteeAccountId: Long? = null
) {
    val isGift: Boolean get() = gifteeAccountId != null
}

enum class SteamStoreGiftFailure {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    NETWORK,
    INVALID_RECIPIENT,
    UNAVAILABLE
}

internal fun steamStoreAccountIdFromSteamId64(steamId: String): Long? {
    val steamId64 = steamId.trim().toULongOrNull() ?: return null
    if (steamId64 < STEAM_ID64_ACCOUNT_BASE) return null
    val accountId = steamId64 - STEAM_ID64_ACCOUNT_BASE
    return accountId.takeIf { it <= UInt.MAX_VALUE.toULong() }?.toLong()
}

private val STEAM_ID64_ACCOUNT_BASE = 76_561_197_960_265_728uL
