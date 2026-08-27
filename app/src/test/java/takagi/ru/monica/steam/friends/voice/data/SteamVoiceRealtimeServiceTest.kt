package takagi.ru.monica.steam.friends.voice.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmRealtimeTransport
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamVoiceRealtimeServiceTest {
    @Test
    fun rotatesVoiceSignalingWhenTheAccountSessionChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeVoiceRealtimeTransport()
        var resolverCalls = 0
        val service = SteamVoiceRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 100L,
            initialRetryMillis = 100L,
            sessionResolver = SteamAccountSessionResolver { current, _ ->
                resolverCalls++
                current.copy(accessToken = "fresh-$resolverCalls")
            }
        )
        val changes = async {
            service.events(account())
                .filterIsInstance<SteamVoiceRealtimeEvent.ConnectionChanged>()
                .take(3)
                .toList()
        }

        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            listOf(
                SteamVoiceRealtimeEvent.ConnectionChanged(true),
                SteamVoiceRealtimeEvent.ConnectionChanged(false),
                SteamVoiceRealtimeEvent.ConnectionChanged(true)
            ),
            changes.await()
        )
        assertEquals(2, resolverCalls)
        assertEquals(1, transport.resetCalls)
        assertEquals(listOf("fresh-1", "fresh-2"), transport.connectedAccessTokens)
        assertEquals(listOf("fresh-1", "fresh-2"), transport.collectedAccessTokens)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_STEAM_ID,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "stale",
        refreshToken = "refresh",
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_STEAM_ID = "76561198000000001"
    }
}

private class FakeVoiceRealtimeTransport : SteamCmRealtimeTransport {
    private val buses = ConcurrentHashMap<String, MutableSharedFlow<SteamCmEnvelope>>()
    private val connected = ConcurrentHashMap.newKeySet<String>()
    val collectedAccessTokens = mutableListOf<String?>()
    val connectedAccessTokens = mutableListOf<String?>()
    var resetCalls = 0
        private set

    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> {
        collectedAccessTokens += account.accessToken
        return buses.getOrPut(account.steamId) { MutableSharedFlow(extraBufferCapacity = 8) }
    }

    override fun connect(account: SteamAccount) {
        connectedAccessTokens += account.accessToken
        connected += account.steamId
    }

    override fun isConnected(account: SteamAccount): Boolean = account.steamId in connected

    override fun reset(account: SteamAccount) {
        resetCalls++
        connected -= account.steamId
    }
}
