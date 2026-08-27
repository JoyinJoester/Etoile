package takagi.ru.monica.steam.friends.chat.position.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatJumpToLatestPolicyTest {
    @Test
    fun scrollingThroughReadHistoryDoesNotShowTheButtonAgain() {
        val messages = messages(100L, 200L, 300L)
        val previous = SteamChatJumpToLatestState(
            initialized = true,
            readThroughTimestamp = 300L,
            latestMessageId = "m300",
            wasAtBottom = true
        )

        val result = reduceSteamChatJumpToLatest(
            previous = previous,
            initialAcknowledgedTimestamp = 300L,
            visibleThroughTimestamp = 100L,
            messagesBelow = 2,
            restored = true,
            messages = messages
        )

        assertFalse(result.visible)
        assertEquals(0, result.unreadBelowCount)
        assertEquals(300L, result.state.readThroughTimestamp)
    }

    @Test
    fun onlyIncomingMessagesAfterTheReadMarkerAreCounted() {
        val messages = listOf(
            SteamChatJumpMessage("m100", 100L, incoming = true),
            SteamChatJumpMessage("m200", 200L, incoming = true),
            SteamChatJumpMessage("m300", 300L, incoming = true),
            SteamChatJumpMessage("m400", 400L, incoming = false)
        )
        val previous = SteamChatJumpToLatestState(
            initialized = true,
            readThroughTimestamp = 200L,
            latestMessageId = "m200",
            wasAtBottom = false
        )

        val result = reduceSteamChatJumpToLatest(
            previous = previous,
            initialAcknowledgedTimestamp = 200L,
            visibleThroughTimestamp = 200L,
            messagesBelow = 2,
            restored = true,
            messages = messages
        )

        assertTrue(result.visible)
        assertEquals(1, result.unreadBelowCount)
    }

    @Test
    fun aMessageStaysReadAfterItEnteredTheViewport() {
        val messages = messages(100L, 200L, 300L)
        val previous = SteamChatJumpToLatestState(
            initialized = true,
            readThroughTimestamp = 200L,
            latestMessageId = "m300",
            wasAtBottom = false
        )
        val read = reduceSteamChatJumpToLatest(
            previous = previous,
            initialAcknowledgedTimestamp = 200L,
            visibleThroughTimestamp = 300L,
            messagesBelow = 0,
            restored = true,
            messages = messages
        )
        val scrolledBack = reduceSteamChatJumpToLatest(
            previous = read.state,
            initialAcknowledgedTimestamp = 200L,
            visibleThroughTimestamp = 100L,
            messagesBelow = 2,
            restored = true,
            messages = messages
        )

        assertFalse(scrolledBack.visible)
        assertEquals(300L, scrolledBack.state.readThroughTimestamp)
    }

    @Test
    fun aConversationAlreadyAtBottomFollowsNewMessagesWithoutShowingTheButton() {
        val previousMessages = messages(100L, 200L)
        val previous = reduceSteamChatJumpToLatest(
            previous = SteamChatJumpToLatestState(),
            initialAcknowledgedTimestamp = 200L,
            visibleThroughTimestamp = 200L,
            messagesBelow = 0,
            restored = true,
            messages = previousMessages
        ).state

        val result = reduceSteamChatJumpToLatest(
            previous = previous,
            initialAcknowledgedTimestamp = 200L,
            visibleThroughTimestamp = 200L,
            messagesBelow = 1,
            restored = true,
            messages = messages(100L, 200L, 300L)
        )

        assertFalse(result.visible)
        assertEquals(300L, result.state.readThroughTimestamp)
    }

    private fun messages(vararg timestamps: Long): List<SteamChatJumpMessage> = timestamps.map { timestamp ->
        SteamChatJumpMessage(
            id = "m$timestamp",
            timestamp = timestamp,
            incoming = true
        )
    }
}
