package takagi.ru.monica.steam.alerts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.alerts.data.SteamAlertAccountSessionProvider
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin
import takagi.ru.monica.steam.session.domain.SteamSessionResolution

class SteamAlertAccountSessionsTest {
    @Test
    fun backgroundResolutionKeepsEachCapturedStorageOrigin() = runTest {
        val room = handle(1L, SteamAccountSessionOrigin(SteamStorageSource.Local))
        val mdbx = handle(
            2L,
            SteamAccountSessionOrigin(
                SteamStorageSource.Mdbx(databaseId = 42L),
                entryId = "entry-2"
            )
        )
        val resolvedOrigins = mutableListOf<String>()
        val snapshot = SteamAlertAccountSessionProvider(
            loadHandles = { listOf(room, mdbx) },
            resolve = { handle ->
                resolvedOrigins += handle.origin.stableKey
                SteamSessionResolution(
                    account = handle.account.copy(accessToken = "fresh-${handle.account.id}"),
                    refreshed = true,
                    refreshAttempted = true
                )
            }
        ).load(refreshSessions = true)

        assertEquals(listOf("room", "mdbx:42:entry-2"), resolvedOrigins)
        assertEquals(listOf(1L, 2L), snapshot.usableAccounts.map(SteamAccount::id))
        assertEquals(0, snapshot.sessionIssues)
    }

    @Test
    fun failedRefreshIsReportedAndExcludedFromNetworkChecks() = runTest {
        val handle = handle(1L, SteamAccountSessionOrigin(SteamStorageSource.Local))
        val snapshot = SteamAlertAccountSessionProvider(
            loadHandles = { listOf(handle) },
            resolve = {
                SteamSessionResolution(
                    account = it.account,
                    refreshed = false,
                    refreshAttempted = true
                )
            }
        ).load(refreshSessions = true)

        assertEquals(1, snapshot.sessionIssues)
        assertEquals(emptyList<SteamAccount>(), snapshot.usableAccounts)
        assertEquals(listOf(handle.account), snapshot.allAccounts)
    }

    @Test
    fun disabledSessionChecksDoNotInvokeTheResolver() = runTest {
        val handle = handle(1L, SteamAccountSessionOrigin(SteamStorageSource.Local))
        var calls = 0
        val snapshot = SteamAlertAccountSessionProvider(
            loadHandles = { listOf(handle) },
            resolve = {
                calls++
                error("resolver must not run")
            }
        ).load(refreshSessions = false)

        assertEquals(0, calls)
        assertEquals(listOf(handle.account), snapshot.usableAccounts)
    }

    private fun handle(id: Long, origin: SteamAccountSessionOrigin) =
        SteamAccountSessionHandle(
            account = SteamAccount(
                id = id,
                steamId = "7656119800000000$id",
                accountName = "account-$id",
                displayName = "Account $id",
                deviceId = "android:test",
                sharedSecret = "secret",
                identitySecret = "identity",
                revocationCode = "R12345",
                tokenGid = "gid",
                accessToken = "access-$id",
                refreshToken = "refresh-$id",
                steamLoginSecure = "7656119800000000$id||access-$id",
                rawSteamGuardJson = "{}",
                selected = false,
                sortOrder = id.toInt(),
                createdAt = 0L,
                updatedAt = 0L
            ),
            origin = origin
        )
}
