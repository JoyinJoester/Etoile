package takagi.ru.monica.steam.store.interest.domain

import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem

internal fun SteamStoreHome.withoutIgnoredGames(ignoredAppIds: Set<Int>): SteamStoreHome {
    if (ignoredAppIds.isEmpty()) return this
    fun List<SteamStoreItem>.visible() = filterNot { it.appId in ignoredAppIds }
    return copy(
        specials = specials.visible(),
        topSellers = topSellers.visible(),
        newReleases = newReleases.visible(),
        comingSoon = comingSoon.visible()
    )
}

internal fun SteamStoreCatalogPage.withoutIgnoredGames(
    ignoredAppIds: Set<Int>
): SteamStoreCatalogPage {
    if (items.isEmpty()) return this
    val serverNextStart = nextStart
    return copy(
        items = if (ignoredAppIds.isEmpty()) items else items.filterNot {
            it.appId in ignoredAppIds
        },
        nextStartOverride = serverNextStart
    )
}

internal fun List<SteamStoreItem>.withoutIgnoredGames(
    ignoredAppIds: Set<Int>
): List<SteamStoreItem> = if (ignoredAppIds.isEmpty()) this else filterNot {
    it.appId in ignoredAppIds
}
