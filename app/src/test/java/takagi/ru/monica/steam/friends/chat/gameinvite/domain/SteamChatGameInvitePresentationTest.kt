package takagi.ru.monica.steam.friends.chat.gameinvite.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent

class SteamChatGameInvitePresentationTest {
    @Test
    fun replacesTheGenericSteamLabelWithResolvedStoreMetadata() {
        val invite = invite(label = "Steam game invitation")

        val presentation = invite.toGameInvitePresentation(
            SteamChatGameInviteMetadata(
                appId = 730,
                name = "Counter-Strike 2",
                headerImageUrl = "https://cdn.example/header.jpg"
            )
        )

        assertEquals("Counter-Strike 2", presentation.gameName)
        assertEquals("https://cdn.example/header.jpg", presentation.artworkUrl)
        assertEquals(730, presentation.appId)
    }

    @Test
    fun keepsARealGameNameAndDropsGenericOrAppOnlyLabels() {
        assertEquals("Team Fortress 2", meaningfulGameInviteLabel("Team Fortress 2"))
        assertNull(meaningfulGameInviteLabel("Steam game invitation"))
        assertNull(meaningfulGameInviteLabel("App 730"))
        assertNull(meaningfulGameInviteLabel("steam://rungameid/730"))
    }

    @Test
    fun providesAStableSteamArtworkFallbackBeforeMetadataLoads() {
        val presentation = invite(label = "App 730").toGameInvitePresentation(metadata = null)

        assertNull(presentation.gameName)
        assertEquals(steamGameInviteHeaderUrl(730), presentation.artworkUrl)
    }

    private fun invite(label: String) = SteamChatRichContent.GameInvite(
        appId = 730,
        lobbyId = null,
        inviterSteamId = null,
        url = "steam://rungameid/730",
        label = label,
        rawBody = label
    )
}
