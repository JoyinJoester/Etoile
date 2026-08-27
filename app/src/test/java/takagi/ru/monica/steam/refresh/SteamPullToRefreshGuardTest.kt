package takagi.ru.monica.steam.refresh

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamPullToRefreshGuardTest {
    @Test
    fun refreshIsOnlyAddedToNetworkBackedPagesWithRealRefreshHandlers() {
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendsList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsList.kt"
        ).readText()
        val friendsViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/presentation/SteamFriendsViewModel.kt"
        ).readText()
        val notificationsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/ui/SteamNotificationsScreen.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val steamViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(friendsScreen.contains("SteamExpressivePullToRefresh("))
        assertTrue(friendsScreen.contains("refreshing = state.loading || state.refreshing"))
        assertTrue(friendsScreen.contains("onRefresh = friendsViewModel::refresh"))
        assertTrue(friendsScreen.contains("enabled = selectedAccount?.hasRealSteamId == true"))
        assertFalse(friendsScreen.contains("PullToSearchStateHandle"))
        assertFalse(friendsList.contains("LinearProgressIndicator"))
        assertTrue(friendsViewModel.contains("fun refresh()"))
        assertTrue(friendsViewModel.contains("gateway.fetch"))

        assertTrue(notificationsScreen.contains("SteamExpressivePullToRefresh("))
        assertTrue(notificationsScreen.contains("refreshing = state.loading"))
        assertTrue(notificationsScreen.contains("onRefresh: () -> Unit"))
        assertTrue(notificationsScreen.contains("enabled = account?.hasRealSteamId == true"))
        assertFalse(notificationsScreen.contains("PullToSearchStateHandle"))
        assertTrue(steamScreen.contains("onRefresh = viewModel::refreshSteamNotifications"))
        assertTrue(steamViewModel.contains("fun refreshSteamNotifications"))
        assertTrue(steamViewModel.contains("notificationService.fetch"))
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
