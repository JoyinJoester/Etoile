package takagi.ru.monica.steam.friends.domain

import takagi.ru.monica.steam.data.SteamAccount

interface SteamFriendsGateway {
    fun fetch(
        account: SteamAccount,
        fetchedAt: Long = System.currentTimeMillis()
    ): SteamFriendsSnapshot

    fun respondToInvite(
        account: SteamAccount,
        friendSteamId: String,
        accept: Boolean
    ): SteamFriendActionResult

    fun changeRelationship(
        account: SteamAccount,
        friendSteamId: String,
        action: SteamFriendRelationshipAction
    ): SteamFriendActionResult

    fun findCandidates(
        account: SteamAccount,
        query: String
    ): List<SteamFriend> = emptyList()
}
