package takagi.ru.monica.steam.friends.domain

import java.util.Locale

fun sortSteamFriendsForList(friends: Iterable<SteamFriend>): List<SteamFriend> =
    friends.sortedWith(
        compareByDescending<SteamFriend> {
            it.relationship == SteamFriendRelationship.REQUEST_INCOMING
        }
            .thenByDescending(SteamFriend::isOnlineOrPlaying)
            .thenByDescending(SteamFriend::isPlaying)
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
            .thenBy(SteamFriend::steamId)
    )
