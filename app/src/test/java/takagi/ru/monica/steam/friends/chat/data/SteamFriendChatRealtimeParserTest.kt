package takagi.ru.monica.steam.friends.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReactionType
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmHeader
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamFriendChatRealtimeParserTest {
    @Test
    fun parsesVersionIndependentIncomingMessagesAndPreservesBbcode() {
        val event = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(
                method = "FriendMessagesClient.IncomingMessage#7",
                entryType = 3,
                body = "[gameinvite appid=570]join[/gameinvite]",
                localEcho = false
            ),
            ACCOUNT_STEAM_ID
        )

        assertTrue(event is SteamChatRealtimeEvent.Message)
        val message = (event as SteamChatRealtimeEvent.Message).message
        assertEquals(PARTNER_STEAM_ID, message.partnerSteamId)
        assertEquals(PARTNER_STEAM_ID, message.senderSteamId)
        assertEquals(1_722_222_222L, message.timestamp)
        assertEquals(9, message.ordinal)
        assertEquals("[gameinvite appid=570]join[/gameinvite]", message.body)
    }

    @Test
    fun localEchoUsesTheActiveAccountAsSender() {
        val event = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(localEcho = true),
            ACCOUNT_STEAM_ID
        ) as SteamChatRealtimeEvent.Message

        assertEquals(ACCOUNT_STEAM_ID, event.message.senderSteamId)
    }

    @Test
    fun preservesSteamEmoteEntriesAsActionMessages() {
        val event = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(entryType = 4, body = "waves"),
            ACCOUNT_STEAM_ID
        ) as SteamChatRealtimeEvent.Message

        assertEquals("/me waves", event.message.body)
    }

    @Test
    fun parsesTypingConversationExitAndAckEvents() {
        val typing = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(entryType = 2, body = "", localEcho = false),
            ACCOUNT_STEAM_ID
        )
        val left = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(entryType = 6, body = "", localEcho = false),
            ACCOUNT_STEAM_ID
        )
        val ack = SteamFriendChatRealtimeParser.parse(
            SteamCmEnvelope(
                eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD,
                header = SteamCmHeader(
                    targetJobName = "FriendMessagesClient.NotifyAckMessageEcho#3"
                ),
                body = SteamProtoWriter().apply {
                    writeFixed64(1, PARTNER_STEAM_ID.toLong())
                    writeVarint(2, 1_722_222_222L)
                }.toByteArray()
            ),
            ACCOUNT_STEAM_ID
        )

        assertEquals(
            SteamChatRealtimeEvent.Typing(PARTNER_STEAM_ID, localEcho = false),
            typing
        )
        assertEquals(
            SteamChatRealtimeEvent.ConversationLeft(PARTNER_STEAM_ID, localEcho = false),
            left
        )
        assertEquals(
            SteamChatRealtimeEvent.Acknowledged(PARTNER_STEAM_ID, 1_722_222_222L),
            ack
        )
    }

    @Test
    fun parsesRealtimeReactionChanges() {
        val event = SteamFriendChatRealtimeParser.parse(
            SteamCmEnvelope(
                eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD,
                header = SteamCmHeader(
                    targetJobName = "FriendMessagesClient.MessageReaction#2"
                ),
                body = SteamProtoWriter().apply {
                    writeFixed64(1, PARTNER_STEAM_ID.toLong())
                    writeVarint(2, 1_722_222_222L)
                    writeVarint(3, 9L)
                    writeFixed64(4, ACCOUNT_STEAM_ID.toLong())
                    writeVarint(5, 1L)
                    writeString(6, "steamthumbsup")
                    writeBool(7, true)
                }.toByteArray()
            ),
            ACCOUNT_STEAM_ID
        ) as SteamChatRealtimeEvent.ReactionChanged

        assertEquals(PARTNER_STEAM_ID, event.partnerSteamId)
        assertEquals(ACCOUNT_STEAM_ID, event.reactorSteamId)
        assertEquals(SteamChatReactionType.EMOTICON, event.reactionType)
        assertEquals("steamthumbsup", event.reactionName)
        assertTrue(event.isAdd)
    }

    @Test
    fun fallsBackToPlainMessageAndIgnoresUnrelatedOrMalformedEvents() {
        val fallback = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(body = "", plainBody = "plain fallback"),
            ACCOUNT_STEAM_ID
        ) as SteamChatRealtimeEvent.Message
        val unrelated = SteamFriendChatRealtimeParser.parse(
            incomingEnvelope(method = "PlayerClient.NotifyLastPlayedTimes#1"),
            ACCOUNT_STEAM_ID
        )
        val malformed = SteamFriendChatRealtimeParser.parse(
            SteamCmEnvelope(
                eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD,
                header = SteamCmHeader(
                    targetJobName = "FriendMessagesClient.IncomingMessage#1"
                ),
                body = byteArrayOf(0x0f)
            ),
            ACCOUNT_STEAM_ID
        )

        assertEquals("plain fallback", fallback.message.body)
        assertNull(unrelated)
        assertNull(malformed)
    }

    private fun incomingEnvelope(
        method: String = "FriendMessagesClient.IncomingMessage#1",
        entryType: Int = 1,
        body: String = "hello",
        plainBody: String = "",
        localEcho: Boolean = false
    ) = SteamCmEnvelope(
        eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD,
        header = SteamCmHeader(targetJobName = method),
        body = SteamProtoWriter().apply {
            writeFixed64(1, PARTNER_STEAM_ID.toLong())
            writeVarint(2, entryType.toLong())
            if (body.isNotEmpty()) writeString(4, body)
            writeFixed32(5, 1_722_222_222L)
            writeVarint(6, 9L)
            writeBool(7, localEcho)
            if (plainBody.isNotEmpty()) writeString(8, plainBody)
        }.toByteArray()
    )

    private companion object {
        const val ACCOUNT_STEAM_ID = "76561198000000001"
        const val PARTNER_STEAM_ID = "76561198000000003"
    }
}
