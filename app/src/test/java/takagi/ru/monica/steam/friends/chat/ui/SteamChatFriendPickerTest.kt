package takagi.ru.monica.steam.friends.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamPersonaState

class SteamChatFriendPickerTest {
    @Test
    fun onlineFriendsStayAboveOfflineFriends() {
        val friends = listOf(
            friend("offline-recent"),
            friend("online", SteamPersonaState.ONLINE),
            friend("away", SteamPersonaState.AWAY)
        )
        val sorted = sortSteamChatFriendsForPicker(friends)

        assertEquals(
            listOf("away", "online", "offline-recent"),
            sorted.map(SteamFriend::steamId)
        )
    }

    private fun friend(
        steamId: String,
        state: SteamPersonaState = SteamPersonaState.OFFLINE
    ) = SteamFriend(
        steamId = steamId,
        personaName = steamId,
        personaState = state
    )
}
