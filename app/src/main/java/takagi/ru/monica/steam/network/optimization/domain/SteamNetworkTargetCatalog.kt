package takagi.ru.monica.steam.network.optimization.domain

data class SteamNetworkOptimizationTarget(
    val hostname: String,
    val minimumProbeAttempts: Int
)

object SteamNetworkTargetCatalog {
    /**
     * Representative HTTPS endpoints used for active route quality probes.
     *
     * Runtime dynamic DNS coverage is broader than this list: [isSteamHostname]
     * also matches Steam service suffixes and known Steam CDN host families so
     * mobile APIs, chat, media, avatars, UGC and newly introduced subdomains do
     * not need to be hard-coded one by one before they benefit from DoH.
     */
    val DEFAULTS: List<SteamNetworkOptimizationTarget> = listOf(
        SteamNetworkOptimizationTarget("store.steampowered.com", 36),
        SteamNetworkOptimizationTarget("steamcommunity.com", 36),
        SteamNetworkOptimizationTarget("api.steampowered.com", 36),
        SteamNetworkOptimizationTarget("login.steampowered.com", 24),
        SteamNetworkOptimizationTarget("help.steampowered.com", 12),
        SteamNetworkOptimizationTarget("media.steampowered.com", 12),
        SteamNetworkOptimizationTarget("shared.akamai.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("shared.fastly.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("community.akamai.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("community.fastly.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("avatars.akamai.steamstatic.com", 8),
        SteamNetworkOptimizationTarget("cdn.akamai.steamstatic.com", 8)
    )

    val hostnames: List<String> = DEFAULTS.map(SteamNetworkOptimizationTarget::hostname)
    val minimumProbeAttemptsByHost: Map<String, Int> = DEFAULTS.associate {
        it.hostname to it.minimumProbeAttempts
    }

    /**
     * Steam/Valve service suffixes that can appear in the mobile client, embedded
     * Steam pages, chat, authentication, store/community APIs and media payloads.
     * Matching a suffix instead of a frozen host list also covers new API subdomains.
     */
    private val STEAM_SERVICE_SUFFIXES: Set<String> = setOf(
        "steampowered.com",
        "steamcommunity.com",
        "steamstatic.com",
        "steamusercontent.com",
        "steamcontent.com",
        "steam-chat.com",
        "steamchat.com",
        "steamgames.com",
        "steamgames.net",
        "steamserver.net",
        "steam-api.com",
        "steamcdn.com",
        "steamcdn.net",
        "steamconnecttest.com",
        "s.team",
        "steam.tv",
        "steamdeck.com",
        "steambroadcast.com",
        "steamchina.com",
        "playartifact.com",
        "underlords.com",
        "valvesoftware.com",
        "valvesoftware.net",
        "valve.net",
        "valvecontent.com",
        "valvecdn.com"
    )

    /** Legacy/third-party CDN names that are still commonly returned by Steam pages/APIs. */
    private val STEAM_CDN_HOSTS: Set<String> = setOf(
        "steamcdn-a.akamaihd.net",
        "steamcommunity-a.akamaihd.net",
        "steamstore-a.akamaihd.net",
        "steamusercontent-a.akamaihd.net",
        "steamuserimages-a.akamaihd.net",
        "steamvideo-a.akamaihd.net",
        "steambroadcast.akamaized.net",
        "steam.cdn.on.net"
    )

    private val STEAM_CDN_SUFFIXES: Set<String> = setOf(
        "akamaihd.net",
        "akamaized.net",
        "qtlglb.com",
        "hwcdn.net"
    )

    fun isSteamHostname(hostname: String): Boolean {
        val normalized = SteamHostsRuleParser.normalizeHostname(hostname)
        if (normalized.isBlank()) return false
        if (STEAM_CDN_HOSTS.contains(normalized)) return true
        if (STEAM_SERVICE_SUFFIXES.any { suffix ->
                normalized == suffix || normalized.endsWith(".$suffix")
            }
        ) {
            return true
        }
        return normalized.contains("steam") && STEAM_CDN_SUFFIXES.any { suffix ->
            normalized.endsWith(".$suffix")
        }
    }
}
