package takagi.ru.monica.steam.network.cm

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamCmPersistentConnectionTest {
    @Test
    fun reusesOneSocketAndCorrelatesConcurrentServiceJobs() {
        val factory = RecordingSocketFactory()
        val events = CopyOnWriteArrayList<SteamCmEnvelope>()
        val connection = SteamCmPersistentConnection(
            socketFactory = factory,
            endpoint = "cm1.steamserver.net:443",
            steamId = TEST_STEAM_ID,
            webLogonToken = "web-token",
            timeoutMillis = 1_000L,
            eventSink = { envelope -> events.add(envelope) }
        )

        val first = connection.execute(
            SteamCmOperation(
                requestEMsg = 10,
                responseEMsg = 11,
                requestBody = byteArrayOf(1),
                targetJobName = "First#1"
            )
        )
        val second = connection.execute(
            SteamCmOperation(
                requestEMsg = 20,
                responseEMsg = 21,
                requestBody = byteArrayOf(2),
                targetJobName = "Second#1"
            )
        )

        assertEquals("response-11", first.decodeToString())
        assertEquals("response-21", second.decodeToString())
        assertEquals(1, factory.openCount)
        assertEquals(2, factory.operationJobIds.distinct().size)
        assertTrue(events.any { it.eMsg == 9_999 })

        connection.close()
    }

    @Test
    fun aDisconnectInvalidatesTheSocketAndTheNextCallReconnects() {
        val factory = RecordingSocketFactory(failFirstOperation = true)
        val connection = SteamCmPersistentConnection(
            socketFactory = factory,
            endpoint = "cm1.steamserver.net:443",
            steamId = TEST_STEAM_ID,
            webLogonToken = "web-token",
            timeoutMillis = 1_000L
        )

        assertTrue(runCatching {
            connection.execute(
                SteamCmOperation(
                    requestEMsg = 10,
                    responseEMsg = 11,
                    requestBody = byteArrayOf(1),
                    targetJobName = "First#1"
                )
            )
        }.isFailure)

        val response = connection.execute(
            SteamCmOperation(
                requestEMsg = 10,
                responseEMsg = 11,
                requestBody = byteArrayOf(2),
                targetJobName = "Retry#1"
            )
        )

        assertEquals("response-11", response.decodeToString())
        assertEquals(2, factory.openCount)
        connection.close()
    }

    private companion object {
        const val TEST_STEAM_ID = 76561198000000001L
    }
}

private class RecordingSocketFactory(
    private val failFirstOperation: Boolean = false
) : (Request, WebSocketListener) -> WebSocket {
    var openCount: Int = 0
        private set
    val operationJobIds = CopyOnWriteArrayList<Long>()
    private val failed = AtomicBoolean(false)

    override fun invoke(request: Request, listener: WebSocketListener): WebSocket {
        openCount++
        val socket = RecordingWebSocket(request, listener, this)
        listener.onOpen(socket, switchingProtocols(request))
        return socket
    }

    private fun switchingProtocols(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(101)
        .message("Switching Protocols")
        .build()

    fun send(socket: RecordingWebSocket, bytes: ByteString): Boolean {
        val envelope = SteamCmProtocol.decodeMessages(bytes.toByteArray()).single()
        if (envelope.eMsg == SteamCmProtocol.EMSG_CLIENT_LOGON) {
            val body = SteamProtoWriter().apply { writeVarint(1, 1L) }.toByteArray()
            socket.listener.onMessage(
                socket,
                ByteString.of(
                    *SteamCmProtocol.encodeMessage(
                        eMsg = SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE,
                        steamId = 76561198000000001L,
                        sessionId = 42,
                        body = body
                    )
                )
            )
            val event = SteamCmProtocol.encodeMessage(
                eMsg = 9_999,
                steamId = 76561198000000001L,
                sessionId = 42,
                body = byteArrayOf(7)
            )
            socket.listener.onMessage(socket, ByteString.of(*event))
            return true
        }
        operationJobIds += envelope.header.jobIdSource
        if (failFirstOperation && failed.compareAndSet(false, true)) {
            socket.listener.onFailure(socket, IOException("simulated disconnect"), null)
            return true
        }
        val response = SteamCmProtocol.encodeMessage(
            eMsg = envelope.eMsg + 1,
            steamId = 76561198000000001L,
            sessionId = 42,
            body = "response-${envelope.eMsg + 1}".toByteArray(),
            jobIdTarget = envelope.header.jobIdSource
        )
        socket.listener.onMessage(socket, ByteString.of(*response))
        return true
    }
}

private class RecordingWebSocket(
    private val requestValue: Request,
    val listener: WebSocketListener,
    private val factory: RecordingSocketFactory
) : WebSocket {
    override fun request(): Request = requestValue

    override fun queueSize(): Long = 0L

    override fun send(text: String): Boolean = true

    override fun send(bytes: ByteString): Boolean = factory.send(this, bytes)

    override fun close(code: Int, reason: String?): Boolean = true

    override fun cancel() = Unit
}
