package takagi.ru.monica.steam.network.cm

import java.io.Closeable
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException

internal data class SteamCmEvent(
    val accountKey: String,
    val envelope: SteamCmEnvelope
)

/** Owns one persistent CM connection per account/storage identity. */
internal class SteamCmConnectionPool(
    private val bootstrap: SteamCmBootstrapLoader,
    private val socketClient: OkHttpClient,
    private val timeoutMillis: Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val bootstrapTtlMillis: Long = DEFAULT_BOOTSTRAP_TTL_MILLIS,
    private val eventSink: (SteamCmEvent) -> Unit = {},
    private val socketFactory: (Request, WebSocketListener) -> WebSocket =
        socketClient::newWebSocket
) : Closeable {
    private val lock = Any()
    private val connections = mutableMapOf<String, Entry>()
    private val bootstraps = mutableMapOf<String, CachedBootstrap>()
    private val bootstrapLocks = mutableMapOf<String, Any>()
    private val latestEvents = mutableMapOf<String, MutableMap<Int, SteamCmEnvelope>>()

    fun execute(
        account: SteamAccount,
        operation: SteamCmOperation,
        accountKey: String = steamCmAccountKey(account)
    ): ByteArray {
        val session = loadBootstrap(account, accountKey)
        var lastFailure: Throwable? = null
        session.endpoints.take(MAX_ENDPOINT_ATTEMPTS).forEach { endpoint ->
            val connection = connectionFor(accountKey, session, endpoint)
            try {
                return connection.execute(operation)
            } catch (error: SteamApiException) {
                throw error
            } catch (error: SteamCmResponseTimeoutException) {
                remove(accountKey, connection)
                invalidateBootstrap(accountKey)
                // The request was sent; retrying could duplicate a successful mutation.
                throw error
            } catch (error: Exception) {
                lastFailure = error
                remove(accountKey, connection)
                invalidateBootstrap(accountKey)
            }
        }
        throw IOException("Steam CM is unavailable", lastFailure)
    }

    fun sendNotification(
        account: SteamAccount,
        operation: SteamCmOperation,
        accountKey: String = steamCmAccountKey(account)
    ) {
        val session = loadBootstrap(account, accountKey)
        var lastFailure: Throwable? = null
        session.endpoints.take(MAX_ENDPOINT_ATTEMPTS).forEach { endpoint ->
            val connection = connectionFor(accountKey, session, endpoint)
            try {
                connection.send(operation)
                return
            } catch (error: SteamApiException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
                remove(accountKey, connection)
                invalidateBootstrap(accountKey)
            }
        }
        throw IOException("Steam CM is unavailable", lastFailure)
    }

    /** Ensures the account's realtime socket is logged on even when no request is pending. */
    fun connect(
        account: SteamAccount,
        accountKey: String = steamCmAccountKey(account)
    ) {
        val session = loadBootstrap(account, accountKey)
        var lastFailure: Throwable? = null
        session.endpoints.take(MAX_ENDPOINT_ATTEMPTS).forEach { endpoint ->
            val connection = connectionFor(accountKey, session, endpoint)
            try {
                connection.connect()
                return
            } catch (error: SteamApiException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
                remove(accountKey, connection)
                invalidateBootstrap(accountKey)
            }
        }
        throw IOException("Steam CM is unavailable", lastFailure)
    }

    fun isConnected(
        account: SteamAccount,
        accountKey: String = steamCmAccountKey(account)
    ): Boolean = synchronized(lock) {
        connections[accountKey]?.connection?.isHealthy() == true
    }

    fun latestEvent(accountKey: String, eMsg: Int): SteamCmEnvelope? = synchronized(lock) {
        latestEvents[accountKey]?.get(eMsg)
    }

    override fun close() {
        val entries = synchronized(lock) {
            val current = connections.values.toList()
            connections.clear()
            bootstraps.clear()
            bootstrapLocks.clear()
            latestEvents.clear()
            current
        }
        entries.forEach { it.connection.close() }
    }

    fun closeAccount(
        account: SteamAccount,
        accountKey: String = steamCmAccountKey(account)
    ) {
        val key = accountKey
        val entry = synchronized(lock) {
            bootstraps.remove(key)
            latestEvents.remove(key)
            connections.remove(key)
        }
        entry?.connection?.close()
    }

    private fun loadBootstrap(account: SteamAccount, accountKey: String): SteamCmBootstrapData {
        val accountBootstrapLock = synchronized(lock) {
            bootstrapLocks.getOrPut(accountKey) { Any() }
        }
        return synchronized(accountBootstrapLock) bootstrap@{
            val fingerprint = accountFingerprint(account)
            val cached = synchronized(lock) {
                bootstraps[accountKey]
                    ?.takeIf { it.fingerprint == fingerprint && it.expiresAtMillis > nowMillis() }
                    ?.data
            }
            if (cached != null) return@bootstrap cached
            val loaded = bootstrap.load(account)
            synchronized(lock) {
                bootstraps[accountKey] = CachedBootstrap(
                    fingerprint = fingerprint,
                    data = loaded,
                    expiresAtMillis = nowMillis() + bootstrapTtlMillis
                )
            }
            loaded
        }
    }

    private fun connectionFor(
        accountKey: String,
        session: SteamCmBootstrapData,
        endpoint: String
    ): SteamCmPersistentConnection {
        synchronized(lock) {
            val current = connections[accountKey]
            if (current != null &&
                current.webLogonToken == session.webLogonToken &&
                current.endpoint == endpoint &&
                current.connection.canBeReused()
            ) {
                return current.connection
            }
            current?.connection?.close()
            val created = SteamCmPersistentConnection(
                socketFactory = socketFactory,
                endpoint = endpoint,
                steamId = session.steamId,
                webLogonToken = session.webLogonToken,
                timeoutMillis = timeoutMillis,
                eventSink = { envelope ->
                    synchronized(lock) {
                        latestEvents.getOrPut(accountKey) { mutableMapOf() }[envelope.eMsg] = envelope
                    }
                    eventSink(SteamCmEvent(accountKey, envelope))
                }
            )
            connections[accountKey] = Entry(
                endpoint = endpoint,
                webLogonToken = session.webLogonToken,
                connection = created
            )
            return created
        }
    }

    private fun remove(accountKey: String, connection: SteamCmPersistentConnection) {
        synchronized(lock) {
            if (connections[accountKey]?.connection === connection) {
                connections.remove(accountKey)
            }
        }
        connection.invalidate()
    }

    private fun invalidateBootstrap(accountKey: String) {
        synchronized(lock) { bootstraps.remove(accountKey) }
    }

    private fun accountFingerprint(account: SteamAccount): String =
        "${account.id}|${account.steamId}|${account.accessToken.orEmpty()}"

    private data class Entry(
        val endpoint: String,
        val webLogonToken: String,
        val connection: SteamCmPersistentConnection
    )

    private data class CachedBootstrap(
        val fingerprint: String,
        val data: SteamCmBootstrapData,
        val expiresAtMillis: Long
    )

    private companion object {
        const val DEFAULT_BOOTSTRAP_TTL_MILLIS = 120_000L
        const val MAX_ENDPOINT_ATTEMPTS = 3
    }
}
