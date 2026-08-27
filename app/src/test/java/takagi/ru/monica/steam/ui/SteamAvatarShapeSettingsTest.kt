package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.foundation.ui.SteamAvatarShapeOption

class SteamAvatarShapeSettingsTest {
    @Test
    fun avatarShapeDefaultsToSquareAndSanitizesStoredValues() {
        assertEquals(SteamAvatarShapeOption.SQUARE, SteamAvatarShapeOption.fromStoredValue(null))
        assertEquals(SteamAvatarShapeOption.SQUARE, SteamAvatarShapeOption.fromStoredValue("unknown"))
        assertEquals(SteamAvatarShapeOption.ROUNDED, SteamAvatarShapeOption.fromStoredValue("rounded"))
        assertEquals(SteamAvatarShapeOption.CIRCLE, SteamAvatarShapeOption.fromStoredValue("circle"))
    }

    @Test
    fun appProviderSeparatesPlainAndFramedAvatarShapes() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamAvatarShapeProvider.kt"
        ).readText()
        val accountAvatar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamAvatarImage.kt"
        ).readText()
        val friendAvatar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendUiSupport.kt"
        ).readText()
        val communityAvatar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityImages.kt"
        ).readText()
        val profileAvatar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/ui/SteamProfileViewerOverview.kt"
        ).readText()

        assertTrue(provider.contains("LocalSteamAvatarShape"))
        assertTrue(provider.contains("LocalSteamAvatarFrameShape"))
        assertTrue(provider.contains("ProvideSteamAvatarShape"))
        assertTrue(provider.contains("preferences.plainShape.collectAsState"))
        assertTrue(provider.contains("preferences.framedShape.collectAsState"))
        assertTrue(provider.contains("RectangleShape"))
        assertTrue(accountAvatar.contains("shape: Shape = LocalSteamAvatarShape.current"))
        assertTrue(friendAvatar.contains("shape = LocalSteamAvatarShape.current"))
        assertTrue(communityAvatar.contains("shape = LocalSteamAvatarShape.current"))
        assertTrue(profileAvatar.contains("shape = LocalSteamAvatarShape.current"))
    }

    @Test
    fun persistedShapesFallBackToLegacyValueAndCanDiverge() {
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/" +
                "SteamAvatarShapePreferences.kt"
        ).readText()

        assertTrue(preferences.contains("val plainShape:"))
        assertTrue(preferences.contains("val framedShape:"))
        assertTrue(preferences.contains("this[key] ?: this[AVATAR_SHAPE_KEY]"))
        assertTrue(preferences.contains("plain_avatar_shape"))
        assertTrue(preferences.contains("framed_avatar_shape"))
        assertTrue(preferences.contains("migrateLegacyShapeIfNeeded"))
        assertTrue(preferences.contains("updatePlainShape"))
        assertTrue(preferences.contains("updateFramedShape"))
    }

    @Test
    fun nativeAppearanceSettingsExposeTwoIndependentAvatarShapeSelections() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val content = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SteamAvatarShapeSettingsContent.kt"
        ).readText()

        assertTrue(host.contains("SteamAvatarShapePreferences"))
        assertTrue(host.contains("avatarShapePreferences.plainShape"))
        assertTrue(host.contains("avatarShapePreferences.framedShape"))
        assertTrue(host.contains("SteamAvatarShapeSettingsItem("))
        assertTrue(host.contains("SteamAvatarFrameShapeSettingsItem("))
        assertTrue(host.contains("SteamAvatarShapeSelectionSheet("))
        assertTrue(host.contains("SteamAvatarFrameShapeSelectionSheet("))
        assertTrue(host.contains("avatarShapePreferences.updatePlainShape("))
        assertTrue(host.contains("avatarShapePreferences.updateFramedShape("))
        assertTrue(content.contains("heightIn(min = 64.dp)"))
        assertTrue(content.contains("SteamAvatarShapeOption.entries"))
        assertTrue(content.contains("steam_avatar_frame_shape_title"))
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
