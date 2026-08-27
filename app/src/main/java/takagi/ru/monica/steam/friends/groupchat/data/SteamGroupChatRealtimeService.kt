package takagi.ru.monica.steam.friends.groupchat.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmClientRealtimeTransport
import takagi.ru.monica.steam.network.cm.SteamCmRealtimeTransport
import takagi.ru.monica.steam.network.cm.supervisedSteamCmEvents
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

/** Keeps group-chat state on the shared account CM connection. */
internal class SteamGroupChatRealtimeService(
    private val transport: SteamCmRealtimeTransport =
        SteamCmClientRealtimeTransport(SteamCmClient()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val healthyCheckMillis: Long = HEALTHY_CHECK_MILLIS,
    private val initialRetryMillis: Long = INITIAL_RETRY_MILLIS,
    private val maximumRetryMillis: Long = MAXIMUM_RETRY_MILLIS,
    private val sessionResolver: SteamAccountSessionResolver? = null
) : SteamGroupChatRealtimeGateway {
    internal constructor(
        cm: SteamCmClient,
        sessionResolver: SteamAccountSessionResolver? = null
    ) : this(
        transport = SteamCmClientRealtimeTransport(cm),
        sessionResolver = sessionResolver
    )

    override fun events(account: SteamAccount): Flow<SteamGroupChatRealtimeEvent> =
        supervisedSteamCmEvents(
            account = account,
            transport = transport,
            ioDispatcher = ioDispatcher,
            healthyCheckMillis = healthyCheckMillis,
            initialRetryMillis = initialRetryMillis,
            maximumRetryMillis = maximumRetryMillis,
            sessionResolver = sessionResolver,
            diagnosticScope = "group",
            parse = { envelope, _ -> SteamGroupChatRealtimeParser.parse(envelope) },
            connectionChanged = SteamGroupChatRealtimeEvent::ConnectionChanged
        )

    private companion object {
        const val HEALTHY_CHECK_MILLIS = 15_000L
        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAXIMUM_RETRY_MILLIS = 30_000L
    }
}
