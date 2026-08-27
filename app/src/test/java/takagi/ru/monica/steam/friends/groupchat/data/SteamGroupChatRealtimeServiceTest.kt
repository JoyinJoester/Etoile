package takagi.ru.monica.steam.friends.groupchat.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmHeader
import takagi.ru.monica.steam.network.cm.SteamCmProtocol
import takagi.ru.monica.steam.network.cm.SteamCmRealtimeTransport
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamGroupChatRealtimeServiceTest {
    @Test
    fun connectsThenForwardsParsedEventsForTheCollectedAccount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGroupRealtimeTransport()
        val service = service(transport, dispatcher)
        val account = account()
        val events = async { service.events(account).take(2).toList() }

        runCurrent()
        transport.emit(account, incomingEnvelope())
        runCurrent()

        val received = events.await()
        assertEquals(SteamGroupChatRealtimeEvent.ConnectionChanged(true), received.first())
        assertTrue(received.last() is SteamGroupChatRealtimeEvent.Message)
        assertEquals(1, transport.connectCalls)
        assertEquals(listOf(ACCOUNT_STEAM_ID), transport.collectedAccounts)
    }

    @Test
    fun retriesAConnectionFailureWithBoundedBackoff() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGroupRealtimeTransport(failuresBeforeSuccess = 1)
        val service = service(
            transport = transport,
            dispatcher = dispatcher,
            initialRetryMillis = 100L,
            maximumRetryMillis = 100L
        )
        val connected = async { service.events(account()).take(1).toList() }

        runCurrent()
        assertEquals(1, transport.connectCalls)
        advanceTimeBy(99L)
        runCurrent()
        assertEquals(1, transport.connectCalls)
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(SteamGroupChatRealtimeEvent.ConnectionChanged(true)), connected.await())
        assertEquals(2, transport.connectCalls)
    }

    @Test
    fun resolvesTheSessionAgainBeforeReconnect() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGroupRealtimeTransport()
        var resolverCalls = 0
        val service = service(
            transport = transport,
            dispatcher = dispatcher,
            healthyCheckMillis = 100L,
            sessionResolver = SteamAccountSessionResolver { account, forceRefresh ->
                resolverCalls++
                assertEquals(false, forceRefresh)
                account.copy(accessToken = "fresh-$resolverCalls")
            }
        )
        val account = account()
        val collector = launch { service.events(account).collect() }

        runCurrent()
        transport.disconnect(account)
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(2, resolverCalls)
        assertEquals(listOf("fresh-1", "fresh-2"), transport.connectedAccessTokens)
        assertEquals(listOf(ACCOUNT_STEAM_ID, ACCOUNT_STEAM_ID), transport.collectedAccounts)
        assertEquals(1, transport.resetCalls)
        collector.cancelAndJoin()
    }

    @Test
    fun connectionCancellationRemainsCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGroupRealtimeTransport(connectCancellation = true)
        val result = async { service(transport, dispatcher).events(account()).toList() }

        runCurrent()

        assertTrue(result.isCancelled)
        assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
    }

    private fun service(
        transport: FakeGroupRealtimeTransport,
        dispatcher: CoroutineDispatcher,
        healthyCheckMillis: Long = 60_000L,
        initialRetryMillis: Long = 100L,
        maximumRetryMillis: Long = 1_000L,
        sessionResolver: SteamAccountSessionResolver? = null
    ) = SteamGroupChatRealtimeService(
        transport = transport,
        ioDispatcher = dispatcher,
        healthyCheckMillis = healthyCheckMillis,
        initialRetryMillis = initialRetryMillis,
        maximumRetryMillis = maximumRetryMillis,
        sessionResolver = sessionResolver
    )

    private fun incomingEnvelope() = SteamCmEnvelope(
        eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT,
        header = SteamCmHeader(targetJobName = "ChatRoomClient.NotifyIncomingChatMessage#1"),
        body = SteamProtoWriter().apply {
            writeUint64(1, GROUP_ID)
            writeUint64(2, CHAT_ID)
            writeFixed64(3, PARTNER_STEAM_ID.toLong())
            writeString(4, "hello")
            writeVarint(5, 1_722_222_222L)
            writeVarint(7, 1L)
        }.toByteArray()
    )

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
        const val PARTNER_STEAM_ID = "76561198000000003"
        const val GROUP_ID = "8001"
        const val CHAT_ID = "9001"
    }
}

private class FakeGroupRealtimeTransport(
    private var failuresBeforeSuccess: Int = 0,
    private val connectCancellation: Boolean = false
) : SteamCmRealtimeTransport {
    private val buses = ConcurrentHashMap<String, MutableSharedFlow<SteamCmEnvelope>>()
    private val connected = ConcurrentHashMap.newKeySet<String>()
    val collectedAccounts = mutableListOf<String>()
    val connectedAccessTokens = mutableListOf<String?>()
    var connectCalls: Int = 0
        private set
    var resetCalls: Int = 0
        private set

    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> {
        collectedAccounts += account.steamId
        return bus(account)
    }

    override fun connect(account: SteamAccount) {
        connectCalls++
        if (connectCancellation) throw CancellationException("cancelled")
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

    fun disconnect(account: SteamAccount) {
        connected -= account.steamId
    }

    suspend fun emit(account: SteamAccount, envelope: SteamCmEnvelope) {
        bus(account).emit(envelope)
    }

    private fun bus(account: SteamAccount): MutableSharedFlow<SteamCmEnvelope> =
        buses.getOrPut(account.steamId) { MutableSharedFlow(extraBufferCapacity = 8) }
}
