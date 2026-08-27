package takagi.ru.monica.steam.library.gamedata.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamGameDataPageTest {
    @Test
    fun buildsCs2AndDotaPersonalGameDataPagesForRealSteamAccount() {
        assertEquals(
            "https://steamcommunity.com/profiles/76561199437517476/gcpd/730/",
            steamGameDataPage("76561199437517476", 730)?.url
        )
        assertEquals(
            "https://steamcommunity.com/profiles/76561199437517476/gcpd/570/",
            steamGameDataPage("76561199437517476", 570)?.url
        )
    }

    @Test
    fun hidesEntryForUnsupportedGameOrInvalidSteamIdentity() {
        assertNull(steamGameDataPage("76561199437517476", 620))
        assertNull(steamGameDataPage("0", 730))
        assertNull(steamGameDataPage(null, 730))
    }

    @Test
    fun replayBrowserPolicyAllowsHttpsAndTrustedLegacyValveDownloads() {
        val replay = "https://replay.example.steamcontent.com/730/match.dem.bz2?token=abc"
        val legacyValveReplay = "http://replay123.valve.net/730/match.dem.bz2"

        assertEquals(replay, SteamReplayBrowserPolicy.normalizedUrl(replay))
        assertEquals(
            legacyValveReplay,
            SteamReplayBrowserPolicy.normalizedUrl(legacyValveReplay)
        )
        assertNull(SteamReplayBrowserPolicy.normalizedUrl("http://downloads.example.com/match.dem"))
        assertNull(SteamReplayBrowserPolicy.normalizedUrl("javascript:alert(1)"))
        assertNull(SteamReplayBrowserPolicy.normalizedUrl("file:///sdcard/match.dem"))
        assertNull(SteamReplayBrowserPolicy.normalizedUrl("https:///match.dem.bz2"))
    }
}
