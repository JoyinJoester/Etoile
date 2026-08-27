package takagi.ru.monica.steam.confirmations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamConfirmationRefreshGuardTest {
    @Test
    fun confirmationBadgeUsesVerifiedCurrentAccountList() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val badgeBlock = screen
            .substringAfter("val notificationSnapshot = uiState.notifications.snapshot")
            .substringBefore("val pendingNotificationCount")

        assertTrue(badgeBlock.contains("uiState.confirmationsAccountId == selectedAccount.id"))
        assertTrue(badgeBlock.contains("uiState.confirmations.size"))
        assertFalse(badgeBlock.contains("notificationSnapshot?.confirmationCount"))
        assertFalse(badgeBlock.contains("maxOf("))
    }

    @Test
    fun confirmationPageHasIndependentRefreshAndFailureState() {
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val confirmationContent = screen
            .substringAfter("private fun SteamConfirmationsContent(")
            .substringBefore("@Composable\nprivate fun SteamConfirmationHistoryCard(")

        assertTrue(viewModel.contains("val confirmationsRefreshing: Boolean = false"))
        assertTrue(viewModel.contains("val confirmationRefreshError: String? = null"))
        assertTrue(viewModel.contains("val confirmationsAccountId: Long? = null"))
        assertTrue(viewModel.contains("snapshot.confirmationCount > 0"))
        assertTrue(viewModel.contains("clearExistingOnFailure = true"))
        assertTrue(viewModel.contains("confirmationRequestIsCurrent("))
        assertTrue(confirmationContent.contains("SteamExpressivePullToRefresh("))
        assertTrue(confirmationContent.contains("onRefresh: () -> Unit"))
        assertTrue(confirmationContent.contains("confirmationRefreshError"))
    }

    @Test
    fun failedConfirmationPayloadIsNotTreatedAsEmptyList() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamConfirmationService.kt"
        ).readText()

        assertFalse(service.contains("if (payload.bool(\"success\") != true) {\n            return emptyList()"))
        assertTrue(service.contains("throw SteamApiException("))
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
