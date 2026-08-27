package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot

class SteamGroupChatCachePolicyTest {
    @Test
    fun groupThreadCacheKeepsRecentHistoryAndOlderFailedMessages() {
        val failed = message(
            timestamp = 1L,
            deliveryState = SteamGroupChatDeliveryState.FAILED_RETRYABLE,
            clientMessageId = "failed-1"
        )
        val sent = (2L..601L).map(::message)

        val bounded = boundSteamGroupChatThreadForCache(
            SteamGroupChatThreadSnapshot(
                accountSteamId = ACCOUNT,
                groupId = GROUP,
                chatId = CHAT,
                messages = listOf(failed) + sent,
                moreAvailable = false,
                fetchedAt = 100L
            )
        )

        assertEquals(501, bounded.messages.size)
        assertEquals("failed-1", bounded.messages.first().clientMessageId)
        assertEquals(102L, bounded.messages[1].timestamp)
        assertEquals(601L, bounded.messages.last().timestamp)
        assertTrue(bounded.moreAvailable)
    }

    private fun message(
        timestamp: Long,
        deliveryState: SteamGroupChatDeliveryState = SteamGroupChatDeliveryState.SENT,
        clientMessageId: String = ""
    ) = SteamGroupChatMessage(
        groupId = GROUP,
        chatId = CHAT,
        senderSteamId = ACCOUNT,
        timestamp = timestamp,
        ordinal = timestamp.toInt(),
        body = "message-$timestamp",
        deliveryState = deliveryState,
        clientMessageId = clientMessageId
    )

    private companion object {
        const val ACCOUNT = "76561198000000001"
        const val GROUP = "123456789012345678"
        const val CHAT = "987654321098765432"
    }
}
