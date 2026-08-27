package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkTargetCatalogTest {
    @Test
    fun matchesSteamMobileAndApiSubdomains() {
        val hosts = listOf(
            "store.steampowered.com",
            "api.steampowered.com",
            "login.steampowered.com",
            "help.steampowered.com",
            "steamcommunity.com",
            "chat.steamcommunity.com",
            "client.steam-chat.com",
            "cdn.steamstatic.com",
            "avatars.akamai.steamstatic.com",
            "images.steamusercontent.com",
            "cdn.steamcontent.com",
            "s.team"
        )

        hosts.forEach { hostname ->
            assertTrue(hostname, SteamNetworkTargetCatalog.isSteamHostname(hostname))
        }
    }

    @Test
    fun matchesKnownSteamCdnFamiliesWithoutClaimingGenericCdnTraffic() {
        val hosts = listOf(
            "steamcdn-a.akamaihd.net",
            "steamcommunity-a.akamaihd.net",
            "steamusercontent-a.akamaihd.net",
            "steambroadcast.akamaized.net",
            "steam.apac.qtlglb.com"
        )

        hosts.forEach { hostname ->
            assertTrue(hostname, SteamNetworkTargetCatalog.isSteamHostname(hostname))
        }
        assertFalse(SteamNetworkTargetCatalog.isSteamHostname("example.akamaihd.net"))
        assertFalse(SteamNetworkTargetCatalog.isSteamHostname("example.com"))
    }
}
