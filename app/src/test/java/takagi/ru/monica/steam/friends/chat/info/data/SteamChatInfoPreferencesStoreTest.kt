package takagi.ru.monica.steam.friends.chat.info.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType

class SteamChatInfoPreferencesStoreTest {
    @Test
    fun conversationKeyIsStableAndIsolatesAccountAndConversation() {
        val direct = SteamChatConversationId("account-a", SteamChatConversationType.DIRECT, "friend-a")

        assertEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy())
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy(accountSteamId = "account-b"))
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy(peerOrGroupId = "friend-b"))
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(
                direct.copy(type = SteamChatConversationType.GROUP, chatId = "room-a")
            )
        )
    }
}
