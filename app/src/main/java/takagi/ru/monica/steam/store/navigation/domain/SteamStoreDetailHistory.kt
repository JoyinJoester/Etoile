package takagi.ru.monica.steam.store.navigation.domain

data class SteamStoreDetailRoute(
    val appId: Int,
    val discoveryCountryCode: String? = null
)

class SteamStoreDetailHistory {
    private val routes = mutableListOf<SteamStoreDetailRoute>()

    fun push(route: SteamStoreDetailRoute) {
        if (route.appId <= 0 || routes.lastOrNull() == route) return
        routes += route
        if (routes.size > MAX_DEPTH) routes.removeAt(0)
    }

    fun pop(): SteamStoreDetailRoute? = routes.removeLastOrNull()

    fun clear() = routes.clear()

    internal val size: Int get() = routes.size

    private companion object {
        const val MAX_DEPTH = 20
    }
}
