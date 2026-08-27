package takagi.ru.monica.steam.friends.chat.background.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

class SteamChatNotificationDecisionTest {
    @Test
    fun acceptsOneServerConfirmedIncomingPartnerMessage() {
        val decision = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message()
        )

        assertTrue(decision is SteamChatNotificationDecision.Notify)
        decision as SteamChatNotificationDecision.Notify
        assertEquals(PARTNER_STEAM_ID, decision.identity.partnerSteamId)
        assertEquals(SteamChatNotificationPreviewKind.TEXT, decision.preview.kind)
        assertEquals("hello from Steam", decision.preview.text)
        assertEquals(64, decision.identity.stableKey.length)
    }

    @Test
    fun filtersOutgoingLocalEchoAndMalformedServerMessages() {
        val outgoing = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message(senderSteamId = ACCOUNT_STEAM_ID)
        )
        val localEcho = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message(clientMessageId = "local-1")
        )
        val malformed = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message(timestamp = 0L)
        )

        assertEquals(
            SteamChatNotificationIgnoreReason.OUTGOING,
            (outgoing as SteamChatNotificationDecision.Ignore).reason
        )
        assertEquals(
            SteamChatNotificationIgnoreReason.INVALID_SERVER_MESSAGE,
            (localEcho as SteamChatNotificationDecision.Ignore).reason
        )
        assertEquals(
            SteamChatNotificationIgnoreReason.INVALID_SERVER_MESSAGE,
            (malformed as SteamChatNotificationDecision.Ignore).reason
        )
    }

    @Test
    fun rejectsAClaimedPartnerThatDoesNotMatchTheSender() {
        val decision = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message(senderSteamId = OTHER_STEAM_ID)
        )

        assertEquals(
            SteamChatNotificationIgnoreReason.INVALID_PARTNER,
            (decision as SteamChatNotificationDecision.Ignore).reason
        )
    }

    @Test
    fun sourceAccountKeyParticipatesInDeduplicationIdentity() {
        val room = SteamChatNotificationPolicy.evaluate(
            accountKey = "room|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message()
        ) as SteamChatNotificationDecision.Notify
        val mdbx = SteamChatNotificationPolicy.evaluate(
            accountKey = "mdbx:7:entry|1|$ACCOUNT_STEAM_ID",
            accountSteamId = ACCOUNT_STEAM_ID,
            message = message()
        ) as SteamChatNotificationDecision.Notify

        assertNotEquals(room.identity.stableKey, mdbx.identity.stableKey)
    }

    @Test
    fun producesUsefulRichMediaPreviewsWithoutRawBbcode() {
        val image = SteamChatNotificationPolicy.preview(
            "[img]https://steamusercontent-a.akamaihd.net/example/photo.png[/img]"
        )
        val sticker = SteamChatNotificationPolicy.preview("/sticker partyparrot")

        assertEquals(SteamChatNotificationPreviewKind.IMAGE, image.kind)
        assertEquals("photo.png", image.text)
        assertEquals(SteamChatNotificationPreviewKind.STICKER, sticker.kind)
        assertEquals("partyparrot", sticker.text)
    }

    @Test
    fun malformedRichMediaRemainsReadable() {
        val preview = SteamChatNotificationPolicy.preview("/sticker %")

        assertEquals(SteamChatNotificationPreviewKind.STICKER, preview.kind)
        assertEquals("%", preview.text)
    }

    @Test
    fun requestRetainsExactStorageOriginAndAllowsMdbxRuntimeIds() {
        val request = SteamChatNotificationRequest(
            origin = SteamAccountSessionOrigin(
                source = SteamStorageSource.Mdbx(7L),
                entryId = "entry-2"
            ),
            accountId = -42L,
            accountSteamId = ACCOUNT_STEAM_ID,
            partnerSteamId = PARTNER_STEAM_ID
        )

        assertTrue(request.isValid)
        assertEquals("mdbx:7:entry-2", request.origin.stableKey)
    }

    @Test
    fun requestRejectsAnInvalidMdbxDatabaseId() {
        val request = SteamChatNotificationRequest(
            origin = SteamAccountSessionOrigin(
                source = SteamStorageSource.Mdbx(0L),
                entryId = "entry-2"
            ),
            accountId = -42L,
            accountSteamId = ACCOUNT_STEAM_ID,
            partnerSteamId = PARTNER_STEAM_ID
        )

        assertTrue(!request.isValid)
    }

    private fun message(
        senderSteamId: String = PARTNER_STEAM_ID,
        timestamp: Long = 1_722_222_222L,
        clientMessageId: String = ""
    ) = SteamChatMessage(
        partnerSteamId = PARTNER_STEAM_ID,
        senderSteamId = senderSteamId,
        timestamp = timestamp,
        ordinal = 3,
        body = "  hello\nfrom   Steam  ",
        deliveryState = SteamChatDeliveryState.SENT,
        clientMessageId = clientMessageId
    )

    private companion object {
        const val ACCOUNT_STEAM_ID = "76561198000000001"
        const val PARTNER_STEAM_ID = "76561198000000003"
        const val OTHER_STEAM_ID = "76561198000000005"
    }
}
