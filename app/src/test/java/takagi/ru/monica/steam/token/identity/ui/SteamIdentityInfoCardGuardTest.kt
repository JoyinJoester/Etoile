package takagi.ru.monica.steam.token.identity.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamIdentityInfoCardGuardTest {
    @Test
    fun identityCardPrioritizesFriendCodeAndRevealsFullIdsOnDemand() {
        val cardFile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/identity/ui/SteamIdentityInfoCard.kt"
        )
        val card = cardFile.readText()

        assertTrue("SteamIdentityInfoCard.kt is too large", cardFile.readLines().size <= 300)
        assertTrue(card.contains("Card("))
        assertTrue(card.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(card.contains("SteamIdentityConverter.fromSteamId64"))
        assertTrue(card.contains("R.string.steam_identity_friend_code"))
        assertTrue(card.contains("onClick = { detailsVisible = true }"))
        assertTrue(card.contains("AlertDialog("))
        assertTrue(card.contains("identity.steamId3"))
        assertTrue(card.contains("identity.steamId2"))
        assertTrue(card.contains("identity.steamId64"))
        assertTrue(card.contains("ClipboardUtils.copyToClipboard("))
        assertTrue(card.contains("sensitive = false"))
        assertTrue(card.contains("LocalUriHandler.current"))
        assertTrue(card.contains("Modifier.fillMaxWidth().heightIn(min = 48.dp)"))
        assertTrue(card.contains("FontFamily.Monospace"))
    }

    @Test
    fun accountDetailUsesTheIdentityCardWithoutDuplicatingItsImplementation() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(screen.contains("import takagi.ru.monica.steam.token.identity.ui.SteamIdentityInfoCard"))
        assertTrue(screen.contains("SteamIdentityInfoCard(steamId64 = account.steamId)"))
        assertFalse(screen.contains("STEAM_0:"))
        assertFalse(screen.contains("[U:1:"))
    }

    @Test
    fun friendAndProfileDetailsReuseTheSameIdentityDisclosure() {
        val friendDetail = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendDetailScreen.kt"
        ).readText()
        val profileOverview = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/ui/" +
                "SteamProfileViewerOverview.kt"
        ).readText()

        assertTrue(friendDetail.contains("SteamIdentityInfoCard(steamId64 = friend.steamId)"))
        assertTrue(profileOverview.contains("SteamIdentityInfoCard(steamId64 = summary.steamId, embedded = true)"))
        assertFalse(friendDetail.contains("value = friend.steamId"))
        assertFalse(profileOverview.contains("value = summary.steamId"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
