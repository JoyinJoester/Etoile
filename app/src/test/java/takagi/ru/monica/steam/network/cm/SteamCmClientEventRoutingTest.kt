package takagi.ru.monica.steam.network.cm

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

@OptIn(ExperimentalCoroutinesApi::class)
class SteamCmClientEventRoutingTest {
    @Test
    fun exposesOnlyEventsMatchingTheResolvedAccountScope() = runTest {
        val events = MutableSharedFlow<SteamCmEvent>(extraBufferCapacity = 8)
        val client = SteamCmClient(events) { account -> "source:${account.id}:${account.steamId}" }
        val account = account(7L, "76561198000000001")
        val expected = SteamCmEnvelope(146, SteamCmHeader(), byteArrayOf(7))
        val received = async { client.eventsFor(account).first() }

        runCurrent()
        events.emit(SteamCmEvent("source:8:${account.steamId}", SteamCmEnvelope(1, SteamCmHeader(), byteArrayOf(8))))
        events.emit(SteamCmEvent("source:7:${account.steamId}", expected))
        runCurrent()

        assertEquals(expected, received.await())
    }

    private fun account(id: Long, steamId: String) = SteamAccount(
        id = id,
        steamId = steamId,
        accountName = "account-$id",
        displayName = "Account $id",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token-$id",
        refreshToken = "refresh-$id",
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = false,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
