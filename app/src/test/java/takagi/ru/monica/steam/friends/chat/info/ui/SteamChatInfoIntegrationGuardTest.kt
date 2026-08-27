package takagi.ru.monica.steam.friends.chat.info.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatInfoIntegrationGuardTest {
    @Test
    fun infoAndSearchRemainIndependentSafeAreaPages() {
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val search = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchScreen.kt"
        ).readText()

        assertTrue(info.contains("statusBarsPadding()"))
        assertTrue(info.contains("navigationBarsPadding()"))
        assertTrue(info.contains("Modifier.size(48.dp)"))
        assertTrue(info.contains("Switch("))
        assertTrue(info.contains("rememberLauncherForActivityResult"))
        assertTrue(info.contains("avatarPicker.launch(\"image/*\")"))
        assertTrue(search.contains("statusBarsPadding()"))
        assertTrue(search.contains("onOpenMessage"))
    }

    @Test
    fun directAndGroupHeadersOpenTheSharedInfoFlow() {
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatSelectedContent.kt"
        ).readText()

        assertTrue(direct.contains("onOpenInfo"))
        assertTrue(group.contains("onOpenInfo"))
        assertTrue(host.contains("SteamChatInfoScreen("))
        assertTrue(host.contains("SteamChatHistorySearchScreen("))
        assertTrue(host.contains("onCreateGroupFromFriend(partnerSteamId)"))
        assertTrue(host.contains("onUpdateGroupAvatar = groupChatViewModel::updateGroupAvatar"))
    }

    @Test
    fun groupAvatarEditButtonLivesOutsideTheRoundedClip() {
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val editor = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamGroupAvatarEditor.kt"
        )

        assertTrue(info.contains("SteamGroupAvatarEditor("))
        assertTrue(editor.isFile)
        val source = editor.readText()
        assertTrue(source.contains("Modifier.align(Alignment.BottomEnd).size(48.dp)"))
        assertTrue(source.contains(".clip(RoundedCornerShape(22))"))
        assertTrue(source.indexOf(".clip(RoundedCornerShape(22))") < source.indexOf("IconButton("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
