package takagi.ru.monica.steam.friends.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReactionType
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

class SteamChatRealtimeReducerTest {
    @Test
    fun selectedIncomingMessageIsImmediateAndAcknowledged() {
        val reducer = SteamChatRealtimeReducer()
        val state = SteamChatUiState(
            accountSteamId = ACCOUNT,
            selectedPartnerSteamId = PARTNER,
            sessions = SteamChatSessionsSnapshot(
                accountSteamId = ACCOUNT,
                sessions = emptyList(),
                fetchedAt = 1L
            ),
            thread = SteamChatThreadSnapshot(
                accountSteamId = ACCOUNT,
                partnerSteamId = PARTNER,
                messages = emptyList(),
                moreAvailable = false,
                fetchedAt = 1L
            )
        )
        val message = incoming("hello", timestamp = 100L, ordinal = 2)

        val effect = reducer.reduce(
            state = state,
            event = SteamChatRealtimeEvent.Message(message),
            accountSteamId = ACCOUNT,
            nowMillis = 2_000L
        )

        assertEquals(listOf(message), effect.state.thread?.messages)
        assertEquals(0, effect.state.sessions?.sessions?.single()?.unreadCount)
        assertEquals(PARTNER, effect.acknowledgePartnerSteamId)
        assertEquals(100L, effect.acknowledgeTimestamp)
    }

    @Test
    fun duplicateServerDeliveryDoesNotIncreaseUnreadTwice() {
        val reducer = SteamChatRealtimeReducer()
        val state = SteamChatUiState(accountSteamId = ACCOUNT)
        val message = incoming("hello", timestamp = 100L, ordinal = 2)

        val first = reducer.reduce(
            state,
            SteamChatRealtimeEvent.Message(message),
            ACCOUNT,
            2_000L
        )
        val second = reducer.reduce(
            first.state,
            SteamChatRealtimeEvent.Message(message),
            ACCOUNT,
            3_000L
        )

        assertEquals(1, first.state.sessions?.sessions?.single()?.unreadCount)
        assertEquals(1, second.state.sessions?.sessions?.single()?.unreadCount)
    }

    @Test
    fun localEchoMergesWithOptimisticClientMessageWithoutCreatingAnotherRow() {
        val reducer = SteamChatRealtimeReducer()
        val pending = SteamChatMessage(
            partnerSteamId = PARTNER,
            senderSteamId = ACCOUNT,
            timestamp = 100L,
            ordinal = Int.MAX_VALUE,
            body = "hello",
            deliveryState = SteamChatDeliveryState.SENDING,
            clientMessageId = "client-1",
            localCreatedAtMillis = 100_000L
        )
        val state = SteamChatUiState(
            accountSteamId = ACCOUNT,
            selectedPartnerSteamId = PARTNER,
            thread = SteamChatThreadSnapshot(
                accountSteamId = ACCOUNT,
                partnerSteamId = PARTNER,
                messages = listOf(pending),
                moreAvailable = false,
                fetchedAt = 1L
            )
        )
        val serverEcho = incoming(
            body = "hello",
            timestamp = 101L,
            ordinal = 3,
            sender = ACCOUNT
        )

        val effect = reducer.reduce(
            state,
            SteamChatRealtimeEvent.Message(serverEcho),
            ACCOUNT,
            2_000L
        )
        val messages = effect.state.thread?.messages.orEmpty()

        assertEquals(1, messages.size)
        assertEquals("client-1", messages.single().clientMessageId)
        assertEquals(SteamChatDeliveryState.SENT, messages.single().deliveryState)
        assertTrue(messages.single().isOutgoing(ACCOUNT))
    }

    @Test
    fun connectionAndTypingEventsUpdateOnlyPresentationState() {
        val reducer = SteamChatRealtimeReducer()
        var state = SteamChatUiState(accountSteamId = ACCOUNT)
        state = reducer.reduce(
            state,
            SteamChatRealtimeEvent.ConnectionChanged(true),
            ACCOUNT,
            1L
        ).state
        state = reducer.reduce(
            state,
            SteamChatRealtimeEvent.Typing(PARTNER, localEcho = false),
            ACCOUNT,
            2L
        ).state

        assertTrue(state.realtimeConnected)
        assertEquals(setOf(PARTNER), state.typingPartnerSteamIds)
    }

    @Test
    fun realtimeReactionUpdatesTheTargetMessageWithoutAddingARow() {
        val message = incoming("hello", timestamp = 100L, ordinal = 2)
        val state = SteamChatUiState(
            accountSteamId = ACCOUNT,
            selectedPartnerSteamId = PARTNER,
            thread = SteamChatThreadSnapshot(
                accountSteamId = ACCOUNT,
                partnerSteamId = PARTNER,
                messages = listOf(message),
                moreAvailable = false,
                fetchedAt = 1L
            )
        )
        val change = SteamChatRealtimeEvent.ReactionChanged(
            partnerSteamId = PARTNER,
            timestamp = 100L,
            ordinal = 2,
            reactorSteamId = ACCOUNT,
            reactionType = SteamChatReactionType.EMOTICON,
            reactionName = "steamthumbsup",
            isAdd = true
        )

        val effect = SteamChatRealtimeReducer().reduce(state, change, ACCOUNT, 2_000L)
        val messages = effect.state.thread?.messages.orEmpty()

        assertEquals(1, messages.size)
        assertEquals("steamthumbsup", messages.single().reactions.single().name)
        assertEquals(listOf(ACCOUNT), messages.single().reactions.single().reactorSteamIds)
        assertTrue(effect.reconcileAuthoritativeState)
    }

    private fun incoming(
        body: String,
        timestamp: Long,
        ordinal: Int,
        sender: String = PARTNER
    ) = SteamChatMessage(
        partnerSteamId = PARTNER,
        senderSteamId = sender,
        timestamp = timestamp,
        ordinal = ordinal,
        body = body
    )

    private companion object {
        const val ACCOUNT = "76561198000000001"
        const val PARTNER = "76561198000000003"
    }
}
