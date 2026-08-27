package takagi.ru.monica.steam.friends.chat.actions.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamChatMessageActionServiceTest {
    @Test
    fun serializesOfficialReactionAndReportRequests() {
        val cm = RecordingCmGateway()
        val service = SteamChatMessageActionService(cm)
        val account = account()
        val incoming = SteamChatMessage(PARTNER_ID, PARTNER_ID, 1_700_000_100L, 7, "spam")

        service.addEmoticonReaction(account, PARTNER_ID, incoming, ":happy:")
        service.reportMessage(account, PARTNER_ID, incoming, SteamChatReportReason.SPAM)

        assertEquals(
            listOf(
                "FriendMessages.UpdateMessageReaction#1",
                "FriendMessages.ReportMessage#1"
            ),
            cm.calls.map { it.first }
        )
        val reaction = SteamProtoReader(cm.calls[0].second).parseAll()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), reaction.map { it.number })
        assertEquals(PARTNER_ID.toLong(), reaction[0].asFixed64)
        assertEquals(1_700_000_100L, reaction[1].asLong)
        assertEquals(7, reaction[2].asInt)
        assertEquals(1, reaction[3].asInt)
        assertEquals(":happy:", reaction[4].asString)
        assertTrue(reaction[5].asBool)

        val report = SteamProtoReader(cm.calls[1].second).parseAll()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), report.map { it.number })
        assertEquals(PARTNER_ID.toLong(), report[0].asFixed64)
        assertEquals(ACCOUNT_ID.toLong(), report[1].asFixed64)
        assertEquals(28, report[4].asInt)
        assertEquals("spam", report[5].asString)
        assertTrue(report[6].asString.isNotBlank())
    }

    private class RecordingCmGateway : SteamCmGateway {
        val calls = mutableListOf<Pair<String, ByteArray>>()
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
            calls += method to request
            return ByteArray(0)
        }
        override fun exchangeClientMessage(
            account: SteamAccount,
            requestEMsg: Int,
            responseEMsg: Int,
            request: ByteArray
        ): ByteArray = error("Unexpected client exchange")
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_ID,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
        const val PARTNER_ID = "76561198000000002"
    }
}
