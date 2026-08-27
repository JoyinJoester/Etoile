package takagi.ru.monica.steam.friends.chat.position.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatJumpToLatestIntegrationGuardTest {
    @Test
    fun jumpButtonUsesAStableUnclippedSlotAndUnreadStateInBothThreads() {
        val button = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/position/ui/SteamChatJumpToLatestUi.kt"
        ).readText()
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val directAdapter = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThreadJumpState.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()

        assertTrue(button.contains("updateTransition"))
        assertTrue(button.contains("SteamChatJumpButtonSlotSize"))
        assertTrue(button.contains("graphicsLayer"))
        assertFalse(button.contains("AnimatedVisibility"))
        assertTrue(direct.contains("rememberDirectSteamChatJumpToLatestState("))
        assertTrue(directAdapter.contains("rememberSteamChatJumpToLatestState("))
        listOf(direct, group).forEach { source ->
            assertTrue(source.contains("unreadBelowCount"))
            assertFalse(source.contains("visible = readingUi.restored && readingUi.messagesBelow > 0"))
        }
        assertTrue(group.contains("rememberSteamChatJumpToLatestState("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
