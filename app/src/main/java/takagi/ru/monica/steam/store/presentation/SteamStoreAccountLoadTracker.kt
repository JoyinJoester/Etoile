package takagi.ru.monica.steam.store.presentation

import takagi.ru.monica.steam.data.SteamStorageSource

internal class SteamStoreAccountLoadTracker {
    private var previousContext: SteamStoreAccountLoadContext? = null

    fun shouldInitialize(
        accountId: Long?,
        storageSource: SteamStorageSource
    ): Boolean {
        val currentContext = SteamStoreAccountLoadContext(accountId, storageSource)
        if (currentContext == previousContext) return false
        previousContext = currentContext
        return true
    }
}

private data class SteamStoreAccountLoadContext(
    val accountId: Long?,
    val storageSource: SteamStorageSource
)
