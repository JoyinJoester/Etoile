package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreHintPreferencesTest {
    @Test
    fun settingsUseAnIndependentDataStoreWithEnabledDefaults() {
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/hints/data/SteamStoreHintPreferences.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/hints/domain/SteamStoreHintModels.kt"
        ).readText()
        val settingsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/hints/ui/SteamStoreHintSettingsScreen.kt"
        ).readText()

        assertTrue(preferences.contains("name = \"steam_store_hint_settings\""))
        assertTrue(preferences.contains("ownership_hints_enabled"))
        assertTrue(preferences.contains("family_sharing_hints_enabled"))
        assertTrue(preferences.contains("wishlist_hints_enabled"))
        assertTrue(preferences.contains("store_tags_enabled"))
        assertTrue(settings.count { it == '=' } >= 4)
        assertTrue(settings.contains("val ownershipHintsEnabled: Boolean = true"))
        assertTrue(settings.contains("val familySharingHintsEnabled: Boolean = true"))
        assertTrue(settings.contains("val wishlistHintsEnabled: Boolean = true"))
        assertTrue(settings.contains("val storeTagsEnabled: Boolean = true"))
        assertTrue(settingsScreen.contains("SettingsItemWithSwitch("))
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
