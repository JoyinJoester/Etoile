package takagi.ru.monica.steam.network.cm

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamCmConnectionPoolConcurrencyTest {
    @Test
    fun concurrentCallersShareOneBootstrapTokenFetch() {
        val bootstrap = BlockingBootstrapLoader()
        val sockets = DelayedLogonSocketFactory()
        val client = OkHttpClient()
        val pool = SteamCmConnectionPool(
            bootstrap = bootstrap,
            socketClient = client,
            timeoutMillis = 5_000L,
            socketFactory = sockets
        )
        val first = CompletableFuture<Unit>()
        val second = CompletableFuture<Unit>()
        val firstThread = connectThread("bootstrap-first", pool, first)

        assertTrue("First bootstrap fetch never started", bootstrap.firstLoadStarted.await(2, TimeUnit.SECONDS))
        val secondThread = connectThread("bootstrap-second", pool, second)
        assertTrue(
            "Second caller never reached bootstrap coordination",
            waitUntil(2_000L) {
                bootstrap.loadCount.get() > 1 ||
                    secondThread.state == Thread.State.BLOCKED ||
                    secondThread.state == Thread.State.WAITING ||
                    secondThread.state == Thread.State.TIMED_WAITING ||
                    secondThread.state == Thread.State.TERMINATED
            }
        )

        bootstrap.releaseFirstLoad.countDown()
        assertTrue("CM logon was never sent", sockets.logonSent.await(2, TimeUnit.SECONDS))
        sockets.completeLogons()
        first.get(2, TimeUnit.SECONDS)
        second.get(2, TimeUnit.SECONDS)

        assertEquals("Concurrent callers must reuse one clientjstoken fetch", 1, bootstrap.loadCount.get())
        assertEquals(1, sockets.openCount.get())
        firstThread.join(1_000L)
        secondThread.join(1_000L)
        pool.close()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun concurrentCallersShareTheAccountConnectionWhileItIsLoggingOn() {
        val sockets = DelayedLogonSocketFactory()
        val client = OkHttpClient()
        val pool = SteamCmConnectionPool(
            bootstrap = SteamCmBootstrapLoader {
                SteamCmBootstrapData(
                    steamId = ACCOUNT_STEAM_ID,
                    webLogonToken = "web-token",
                    endpoints = listOf("cm1.steamserver.net:443")
                )
            },
            socketClient = client,
            timeoutMillis = 5_000L,
            socketFactory = sockets
        )
        val first = CompletableFuture<Unit>()
        val second = CompletableFuture<Unit>()
        val firstThread = connectThread("cm-first", pool, first)

        assertTrue("First caller never sent CM logon", sockets.logonSent.await(2, TimeUnit.SECONDS))
        val secondThread = connectThread("cm-second", pool, second)
        assertTrue(
            "Second caller never reached the in-flight CM connection",
            waitUntil(2_000L) {
                sockets.openCount.get() > 1 ||
                    secondThread.state == Thread.State.TIMED_WAITING ||
                    secondThread.state == Thread.State.WAITING ||
                    secondThread.state == Thread.State.TERMINATED
            }
        )

        sockets.completeLogons()
        first.get(2, TimeUnit.SECONDS)
        second.get(2, TimeUnit.SECONDS)

        assertEquals("Only one WebSocket may be opened for the same account", 1, sockets.openCount.get())
        assertEquals("An in-progress account socket must not be closed", 0, sockets.closeCount.get())
        firstThread.join(1_000L)
        secondThread.join(1_000L)
        pool.close()
        client.dispatcher.executorService.shutdown()
    }

    private fun connectThread(
        name: String,
        pool: SteamCmConnectionPool,
        result: CompletableFuture<Unit>
    ): Thread = thread(name = name, start = true) {
        runCatching { pool.connect(account()) }
            .onSuccess { result.complete(Unit) }
            .onFailure(result::completeExceptionally)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L))
        }
        return condition()
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_STEAM_ID.toString(),
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_STEAM_ID = 76_561_198_000_000_001L
    }
}

private class BlockingBootstrapLoader : SteamCmBootstrapLoader {
    val loadCount = AtomicInteger()
    val firstLoadStarted = CountDownLatch(1)
    val releaseFirstLoad = CountDownLatch(1)

    override fun load(account: SteamAccount): SteamCmBootstrapData {
        val call = loadCount.incrementAndGet()
        if (call == 1) {
            firstLoadStarted.countDown()
            assertTrue("Timed out waiting to release bootstrap", releaseFirstLoad.await(3, TimeUnit.SECONDS))
        }
        return SteamCmBootstrapData(
            steamId = account.steamId.toLong(),
            webLogonToken = "web-token-$call",
            endpoints = listOf("cm1.steamserver.net:443")
        )
    }
}

private class DelayedLogonSocketFactory : (Request, WebSocketListener) -> WebSocket {
    val openCount = AtomicInteger()
    val closeCount = AtomicInteger()
    val logonSent = CountDownLatch(1)
    private val sockets = CopyOnWriteArrayList<DelayedLogonWebSocket>()

    override fun invoke(request: Request, listener: WebSocketListener): WebSocket {
        openCount.incrementAndGet()
        val socket = DelayedLogonWebSocket(request, listener, this)
        sockets += socket
        listener.onOpen(
            socket,
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()
        )
        return socket
    }

    fun onSend(socket: DelayedLogonWebSocket, bytes: ByteString): Boolean {
        val envelope = SteamCmProtocol.decodeMessages(bytes.toByteArray()).single()
        if (envelope.eMsg == SteamCmProtocol.EMSG_CLIENT_LOGON) logonSent.countDown()
        return true
    }

    fun completeLogons() {
        sockets.forEach { socket ->
            val body = SteamProtoWriter().apply { writeVarint(1, 1L) }.toByteArray()
            socket.listener.onMessage(
                socket,
                ByteString.of(
                    *SteamCmProtocol.encodeMessage(
                        eMsg = SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE,
                        steamId = ACCOUNT_STEAM_ID,
                        sessionId = 42,
                        body = body
                    )
                )
            )
        }
    }

    fun onClosed() {
        closeCount.incrementAndGet()
    }

    private companion object {
        const val ACCOUNT_STEAM_ID = 76_561_198_000_000_001L
    }
}

private class DelayedLogonWebSocket(
    private val requestValue: Request,
    val listener: WebSocketListener,
    private val factory: DelayedLogonSocketFactory
) : WebSocket {
    override fun request(): Request = requestValue
    override fun queueSize(): Long = 0L
    override fun send(text: String): Boolean = true
    override fun send(bytes: ByteString): Boolean = factory.onSend(this, bytes)
    override fun close(code: Int, reason: String?): Boolean {
        factory.onClosed()
        return true
    }
    override fun cancel() = factory.onClosed()
}
