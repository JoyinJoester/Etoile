package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot

class SteamChatSendRecoveryTest {
    @Test
    fun cancellationIsNeverConvertedIntoASendFailure() = runTest {
        val gateway = object : SteamChatGateway {
            override fun fetchSessions(account: SteamAccount) =
                SteamChatSessionsSnapshot(account.steamId, emptyList(), 0L)

            override fun fetchMessages(
                account: SteamAccount,
                partnerSteamId: String,
                before: SteamChatHistoryBoundary?
            ) = SteamChatPage(emptyList(), false)

            override fun sendMessage(
                account: SteamAccount,
                partnerSteamId: String,
                body: String,
                clientMessageId: String
            ): SteamChatMessage = throw CancellationException("account changed")

            override fun acknowledge(
                account: SteamAccount,
                partnerSteamId: String,
                timestamp: Long
            ) = Unit
        }
        var propagated = false

        try {
            sendSteamChatMessageWithSessionRecovery(
                gateway = gateway,
                account = account(),
                partnerSteamId = PARTNER,
                pending = SteamChatMessage(
                    partnerSteamId = PARTNER,
                    senderSteamId = ACCOUNT,
                    timestamp = 100L,
                    ordinal = Int.MAX_VALUE,
                    body = "hello",
                    clientMessageId = "client-1",
                    localCreatedAtMillis = 100_000L
                ),
                sessionResolver = null
            )
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT,
        accountName = "account",
        displayName = "Account",
        deviceId = "device",
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
        const val ACCOUNT = "76561198000000001"
        const val PARTNER = "76561198000000002"
    }
}
