package takagi.ru.monica.steam.store.gift.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsCache
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot

class SteamStoreGiftFriendRepository(
    private val gateway: SteamFriendsGateway,
    private val cache: SteamFriendsCache
) {
    fun loadCached(account: SteamAccount): SteamFriendsSnapshot? = cache.load(account.steamId)

    fun refresh(
        account: SteamAccount,
        fetchedAt: Long = System.currentTimeMillis()
    ): SteamFriendsSnapshot = gateway.fetch(account, fetchedAt).also { snapshot ->
        cache.save(account.steamId, snapshot)
    }
}
