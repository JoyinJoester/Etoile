package takagi.ru.monica.steam.friends.chat.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmClientRealtimeTransport
import takagi.ru.monica.steam.network.cm.SteamCmRealtimeTransport
import takagi.ru.monica.steam.network.cm.supervisedSteamCmEvents
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

/**
 * Adapter from the shared account CM connection to friend-chat domain events.
 * It keeps the socket authenticated while collected and retries without
 * creating a second feature-owned WebSocket.
 */
internal class SteamFriendChatRealtimeService(
    private val transport: SteamCmRealtimeTransport =
        SteamCmClientRealtimeTransport(SteamCmClient()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val healthyCheckMillis: Long = HEALTHY_CHECK_MILLIS,
    private val initialRetryMillis: Long = INITIAL_RETRY_MILLIS,
    private val maximumRetryMillis: Long = MAXIMUM_RETRY_MILLIS,
    private val sessionResolver: SteamAccountSessionResolver? = null
) : SteamChatRealtimeGateway {
    internal constructor(
        cm: SteamCmClient,
        sessionResolver: SteamAccountSessionResolver? = null
    ) : this(
        transport = SteamCmClientRealtimeTransport(cm),
        sessionResolver = sessionResolver
    )

    override fun events(account: SteamAccount): Flow<SteamChatRealtimeEvent> =
        supervisedSteamCmEvents(
            account = account,
            transport = transport,
            ioDispatcher = ioDispatcher,
            healthyCheckMillis = healthyCheckMillis,
            initialRetryMillis = initialRetryMillis,
            maximumRetryMillis = maximumRetryMillis,
            sessionResolver = sessionResolver,
            diagnosticScope = "chat",
            parse = { envelope, current ->
                SteamFriendChatRealtimeParser.parse(envelope, current.steamId)
            },
            connectionChanged = SteamChatRealtimeEvent::ConnectionChanged
        )

    private companion object {
        const val HEALTHY_CHECK_MILLIS = 15_000L
        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAXIMUM_RETRY_MILLIS = 30_000L
    }
}
