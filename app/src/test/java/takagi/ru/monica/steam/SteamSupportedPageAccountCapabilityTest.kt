package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSupportedPageAccountCapabilityTest {
    @Test
    fun sessionPagesFilterToAuthenticatedSessions() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val librarySource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()
        val communitySource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityScreen.kt"
        ).readText()
        val chatSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val dialogSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreenDialogs.kt"
        ).readText()
        val friendsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val freebieSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/freebie/presentation/SteamFreebieViewModel.kt"
        ).readText()

        assertTrue(storeSource.contains("sourceState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(librarySource.contains("sourceState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(communitySource.contains("accountState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(chatSource.contains("accountSourceState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(dialogSource.contains("accountSourceState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(friendsSource.contains("steamState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(freebieSource.contains("sourceState.accounts.filter { it.hasAuthenticatedSession }"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
