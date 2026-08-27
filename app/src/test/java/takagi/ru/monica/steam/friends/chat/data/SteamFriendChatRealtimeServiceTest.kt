package takagi.ru.monica.steam.friends.chat.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmHeader
import takagi.ru.monica.steam.network.cm.SteamCmProtocol
import takagi.ru.monica.steam.network.cm.SteamCmRealtimeTransport
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamFriendChatRealtimeServiceTest {
    @Test
    fun connectsThenForwardsParsedEventsForTheCollectedAccount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport()
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 60_000L,
            initialRetryMillis = 100L,
            maximumRetryMillis = 1_000L
        )
        val account = account(1L, ACCOUNT_STEAM_ID)
        val events = async { service.events(account).take(2).toList() }

        runCurrent()
        transport.emit(account, incomingEnvelope())
        runCurrent()

        val received = events.await()
        assertEquals(SteamChatRealtimeEvent.ConnectionChanged(true), received.first())
        assertTrue(received.last() is SteamChatRealtimeEvent.Message)
        assertEquals(1, transport.connectCalls)
        assertEquals(listOf(ACCOUNT_STEAM_ID), transport.collectedAccounts)
    }

    @Test
    fun retriesAConnectionFailureWithBoundedBackoff() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport(failuresBeforeSuccess = 1)
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 60_000L,
            initialRetryMillis = 100L,
            maximumRetryMillis = 100L
        )
        val connected = async { service.events(account(1L, ACCOUNT_STEAM_ID)).take(1).toList() }

        runCurrent()
        assertEquals(1, transport.connectCalls)
        advanceTimeBy(99L)
        runCurrent()
        assertEquals(1, transport.connectCalls)
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(SteamChatRealtimeEvent.ConnectionChanged(true)), connected.await())
        assertEquals(2, transport.connectCalls)
    }

    @Test
    fun resolvesTheAccountBeforeOpeningTheCmTransport() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport()
        var resolverCalls = 0
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 60_000L,
            sessionResolver = SteamAccountSessionResolver { account, forceRefresh ->
                resolverCalls++
                assertEquals(false, forceRefresh)
                account.copy(accessToken = "fresh-token")
            }
        )
        val events = async { service.events(account(1L, ACCOUNT_STEAM_ID)).take(1).toList() }

        runCurrent()

        assertEquals(1, resolverCalls)
        assertEquals(listOf("fresh-token"), transport.collectedAccessTokens)
        assertEquals(listOf(SteamChatRealtimeEvent.ConnectionChanged(true)), events.await())
    }

    @Test
    fun retriesSessionResolutionBeforeOpeningTheSocket() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport()
        var resolverCalls = 0
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 60_000L,
            initialRetryMillis = 100L,
            maximumRetryMillis = 100L,
            sessionResolver = SteamAccountSessionResolver { current, _ ->
                resolverCalls++
                if (resolverCalls == 1) throw IOException("session store unavailable")
                current.copy(accessToken = "fresh-token")
            }
        )
        val connected = async { service.events(account(1L, ACCOUNT_STEAM_ID)).first() }

        runCurrent()
        assertEquals(0, transport.connectCalls)
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(SteamChatRealtimeEvent.ConnectionChanged(true), connected.await())
        assertEquals(2, resolverCalls)
        assertEquals(listOf("fresh-token"), transport.connectedAccessTokens)
        assertEquals(listOf("fresh-token"), transport.collectedAccessTokens)
    }

    @Test
    fun rotatesTheCollectorAndSocketWhenSessionCredentialsChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport()
        var resolverCalls = 0
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 100L,
            initialRetryMillis = 100L,
            sessionResolver = SteamAccountSessionResolver { current, _ ->
                resolverCalls++
                current.copy(accessToken = "fresh-$resolverCalls")
            }
        )
        val source = account(1L, ACCOUNT_STEAM_ID)
        val changes = async {
            service.events(source)
                .filterIsInstance<SteamChatRealtimeEvent.ConnectionChanged>()
                .take(3)
                .toList()
        }

        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            listOf(
                SteamChatRealtimeEvent.ConnectionChanged(true),
                SteamChatRealtimeEvent.ConnectionChanged(false),
                SteamChatRealtimeEvent.ConnectionChanged(true)
            ),
            changes.await()
        )
        assertEquals(2, resolverCalls)
        assertEquals(1, transport.resetCalls)
        assertEquals(listOf("fresh-1", "fresh-2"), transport.connectedAccessTokens)
        assertEquals(listOf("fresh-1", "fresh-2"), transport.collectedAccessTokens)
    }

    @Test
    fun restartsAnEventCollectorThatStopsUnexpectedly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeRealtimeTransport()
        val service = SteamFriendChatRealtimeService(
            transport = transport,
            ioDispatcher = dispatcher,
            healthyCheckMillis = 60_000L
        )
        val source = account(1L, ACCOUNT_STEAM_ID)
        val changes = async {
            service.events(source)
                .filterIsInstance<SteamChatRealtimeEvent.ConnectionChanged>()
                .take(3)
                .toList()
        }

        runCurrent()
        transport.failCollector(source)
        runCurrent()

        assertEquals(
            listOf(
                SteamChatRealtimeEvent.ConnectionChanged(true),
                SteamChatRealtimeEvent.ConnectionChanged(false),
                SteamChatRealtimeEvent.ConnectionChanged(true)
            ),
            changes.await()
        )
        assertEquals(1, transport.resetCalls)
        assertEquals(2, transport.collectedAccounts.size)
    }

    @Test
    fun propagatesCancellationFromTheConnectionBoundary() = runTest {
        val service = SteamFriendChatRealtimeService(
            transport = FakeRealtimeTransport(
                terminalConnectFailure = CancellationException("cancelled")
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            healthyCheckMillis = 60_000L
        )
        var cancellationPropagated = false

        try {
            service.events(account(1L, ACCOUNT_STEAM_ID)).first()
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
    }

    private fun incomingEnvelope() = SteamCmEnvelope(
        eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD,
        header = SteamCmHeader(targetJobName = "FriendMessagesClient.IncomingMessage#1"),
        body = SteamProtoWriter().apply {
            writeFixed64(1, PARTNER_STEAM_ID.toLong())
            writeVarint(2, 1L)
            writeString(4, "hello")
            writeFixed32(5, 1_722_222_222L)
            writeVarint(6, 1L)
            writeBool(7, false)
        }.toByteArray()
    )

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

    private companion object {
        const val ACCOUNT_STEAM_ID = "76561198000000001"
        const val PARTNER_STEAM_ID = "76561198000000003"
    }
}

private class FakeRealtimeTransport(
    private var failuresBeforeSuccess: Int = 0,
    private val terminalConnectFailure: Throwable? = null
) : SteamCmRealtimeTransport {
    private val buses = ConcurrentHashMap<String, MutableSharedFlow<SteamCmEnvelope>>()
    private val collectorFailures = ConcurrentHashMap<String, MutableSharedFlow<Throwable>>()
    private val connected = ConcurrentHashMap.newKeySet<String>()
    val collectedAccounts = mutableListOf<String>()
    val collectedAccessTokens = mutableListOf<String?>()
    val connectedAccessTokens = mutableListOf<String?>()
    var connectCalls: Int = 0
        private set
    var resetCalls: Int = 0
        private set

    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> {
        collectedAccounts += account.steamId
        collectedAccessTokens += account.accessToken
        return merge(
            bus(account),
            failureBus(account).map<Throwable, SteamCmEnvelope> { throw it }
        )
    }

    override fun connect(account: SteamAccount) {
        connectCalls++
        terminalConnectFailure?.let { throw it }
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw IOException("offline")
        }
        connectedAccessTokens += account.accessToken
        connected += account.steamId
    }

    override fun isConnected(account: SteamAccount): Boolean = account.steamId in connected

    override fun reset(account: SteamAccount) {
        resetCalls++
        connected -= account.steamId
    }

    suspend fun emit(account: SteamAccount, envelope: SteamCmEnvelope) {
        bus(account).emit(envelope)
    }

    suspend fun failCollector(account: SteamAccount) {
        failureBus(account).emit(IOException("collector stopped"))
    }

    private fun bus(account: SteamAccount): MutableSharedFlow<SteamCmEnvelope> =
        buses.getOrPut(account.steamId) { MutableSharedFlow(extraBufferCapacity = 8) }

    private fun failureBus(account: SteamAccount): MutableSharedFlow<Throwable> =
        collectorFailures.getOrPut(account.steamId) { MutableSharedFlow(extraBufferCapacity = 8) }
}
