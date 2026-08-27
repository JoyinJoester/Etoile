package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreHintUiGuardTest {
    @Test
    fun storeUsesSemanticCompactBadgesWithoutPerCardDetailRequests() {
        val badges = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/hints/ui/SteamStoreHintBadges.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val discovery = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDiscoveryContent.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val hintLoader = viewModel
            .substringAfter("private fun loadLibraryHints")
            .substringBefore("fun openStoreWeb")

        assertTrue(badges.contains("FlowRow("))
        assertTrue(badges.contains("MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(badges.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(badges.contains("MaterialTheme.colorScheme.tertiaryContainer"))
        assertTrue(badges.contains("Icon("))
        assertTrue(screen.contains("resolveSteamStoreItemHints("))
        assertTrue(screen.contains("resolveSteamStoreDetailHints("))
        assertTrue(screen.contains("SteamStoreHintBadges("))
        assertTrue(screen.contains("showTags = hintSettings.storeTagsEnabled"))
        assertTrue(screen.contains("SteamStoreTagBadges("))
        assertTrue(screen.contains("labels = detail.tags"))
        assertTrue(discovery.contains("itemHints: (Int) -> List<SteamStoreHintKind>"))
        assertTrue(hintLoader.contains("libraryCacheRepository?.getLibrary(accountId)"))
        assertFalse(hintLoader.contains("service."))
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
