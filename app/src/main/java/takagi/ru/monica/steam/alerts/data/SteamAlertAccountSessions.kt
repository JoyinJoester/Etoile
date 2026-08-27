package takagi.ru.monica.steam.alerts.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamSessionResolution

internal data class SteamAlertAccountSessionSnapshot(
    val allAccounts: List<SteamAccount>,
    val usableAccounts: List<SteamAccount>,
    val sessionIssues: Int
)

/** Resolves background alert accounts without losing their storage origins. */
internal class SteamAlertAccountSessionProvider(
    private val loadHandles: suspend () -> List<SteamAccountSessionHandle>,
    private val resolve: suspend (SteamAccountSessionHandle) -> SteamSessionResolution
) {
    suspend fun load(refreshSessions: Boolean): SteamAlertAccountSessionSnapshot {
        val handles = loadHandles()
        val usable = mutableListOf<SteamAccount>()
        var issues = 0
        handles.forEach { handle ->
            val resolution = if (refreshSessions) {
                runCatching { resolve(handle) }.getOrNull()
            } else {
                SteamSessionResolution(
                    account = handle.account,
                    refreshed = false,
                    refreshAttempted = false
                )
            }
            val account = resolution?.account
            val missingSession = account == null ||
                (account.accessToken.isNullOrBlank() && account.refreshToken.isNullOrBlank())
            val failedRefresh = refreshSessions &&
                resolution?.refreshAttempted == true &&
                !resolution.refreshed
            if (missingSession || failedRefresh) {
                issues++
            } else {
                usable += requireNotNull(account)
            }
        }
        return SteamAlertAccountSessionSnapshot(
            allAccounts = handles.map(SteamAccountSessionHandle::account),
            usableAccounts = usable,
            sessionIssues = issues
        )
    }
}
