package takagi.ru.monica.steam.session

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin
import takagi.ru.monica.steam.session.domain.SteamAccountSessionRefresher
import takagi.ru.monica.steam.session.domain.SteamAccountSessionStore
import takagi.ru.monica.steam.session.domain.SteamSessionTokens

@OptIn(ExperimentalCoroutinesApi::class)
class SteamAccountSessionManagerTest {
    @Test
    fun concurrentRequestsForOneOriginShareOneRefreshAndOnePersistence() = runTest {
        val refresher = FakeRefresher()
        val persisted = RecordingStore()
        val manager = SteamAccountSessionManager(
            refresher = refresher,
            store = persisted,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            nowSeconds = { 2_000L }
        )
        val handle = handle(account(accessToken = "expired"))

        val jobs = List(8) { async { manager.resolve(handle) } }
        advanceUntilIdle()
        val results = jobs.awaitAll()

        assertEquals(1, refresher.calls.get())
        assertEquals(1, persisted.handles.size)
        assertTrue(results.all { it.account.accessToken == "fresh-76561198000000001" })
        assertEquals(handle.origin, persisted.handles.single().origin)
    }

    @Test
    fun differentOriginsRefreshIndependently() = runTest {
        val refresher = FakeRefresher()
        val manager = SteamAccountSessionManager(
            refresher = refresher,
            store = RecordingStore(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            nowSeconds = { 2_000L }
        )

        val first = async { manager.resolve(handle(account(id = 1L, accessToken = "expired"))) }
        val second = async { manager.resolve(handle(account(id = 2L, accessToken = "expired"))) }
        advanceUntilIdle()
        first.await()
        second.await()

        assertEquals(2, refresher.calls.get())
    }

    @Test
    fun aFreshResultIsReusedForAStaleCallerWithoutAnotherRefresh() = runTest {
        val refresher = FakeRefresher()
        val manager = SteamAccountSessionManager(
            refresher = refresher,
            store = RecordingStore(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            nowSeconds = { 2_000L }
        )
        val stale = handle(account(accessToken = "expired"))

        val first = async { manager.resolve(stale) }
        advanceUntilIdle()
        first.await()
        val second = manager.resolve(stale)

        assertEquals(1, refresher.calls.get())
        assertEquals("fresh-76561198000000001", second.account.accessToken)
    }

    private class FakeRefresher : SteamAccountSessionRefresher {
        val calls = AtomicInteger(0)

        override fun shouldRefresh(account: SteamAccount, nowSeconds: Long): Boolean =
            account.accessToken != "fresh-${account.steamId}"

        override suspend fun refresh(
            account: SteamAccount,
            force: Boolean
        ): SteamSessionTokens {
            calls.incrementAndGet()
            delay(10)
            return SteamSessionTokens(
                accessToken = "fresh-${account.steamId}",
                refreshToken = "rotated-refresh"
            )
        }
    }

    private class RecordingStore : SteamAccountSessionStore {
        val handles = Collections.synchronizedList(mutableListOf<SteamAccountSessionHandle>())

        override suspend fun persist(handle: SteamAccountSessionHandle) {
            handles += handle
        }
    }

    private fun handle(account: SteamAccount): SteamAccountSessionHandle =
        SteamAccountSessionHandle(
            account = account,
            origin = SteamAccountSessionOrigin(
                source = SteamStorageSource.Mdbx(databaseId = 42L),
                entryId = "entry-${account.id}"
            )
        )

    private fun account(
        id: Long = 1L,
        accessToken: String?
    ): SteamAccount = SteamAccount(
        id = id,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "shared",
        identitySecret = "identity",
        revocationCode = "R12345",
        tokenGid = "gid",
        accessToken = accessToken,
        refreshToken = "stored-refresh",
        steamLoginSecure = accessToken?.let { "76561198000000001||$it" },
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
