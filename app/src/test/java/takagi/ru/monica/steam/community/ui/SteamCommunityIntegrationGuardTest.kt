package takagi.ru.monica.steam.community.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCommunityIntegrationGuardTest {
    @Test
    fun communityOpensAsAnIndependentSecondaryPageFromTheCapsuleMenu() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityScreen.kt"
        ).readText()
        val dockPages = activity
            .substringAfter("private fun EtoilePage.isDockPage()")
            .substringBefore("private fun EtoilePage.toDockTab()")

        assertTrue(activity.contains("EtoilePage.COMMUNITY"))
        assertTrue(activity.contains("SteamCommunityScreen("))
        assertTrue(activity.contains("pendingCommunitySteamId"))
        assertTrue(dockPages.contains("EtoilePage.COMMUNITY"))
        assertTrue(tokenScreen.contains("onOpenCommunity"))
        assertTrue(tokenScreen.contains("R.string.steam_community_title"))
        assertTrue(screen.contains("ExpressiveTopBar("))
        assertTrue(screen.contains("SteamExpressivePullToRefresh("))
        assertTrue(screen.contains("SteamAccountSwitcherSheet("))
        assertTrue(screen.contains("initialSteamId"))
        assertTrue(screen.contains("accountSource.selectAccount(requestedAccount.id)"))
        assertTrue(screen.contains("statusBarsPadding()"))
        assertTrue(screen.contains("SteamWebBrowserScreen("))
        assertTrue(screen.contains("communityWebUrl = url"))
        assertTrue(screen.contains("SteamWebNavigationPolicy.isAllowed(url)"))
        assertFalse(screen.contains("Intent.ACTION_VIEW"))
        assertFalse(screen.contains("TopAppBar("))
    }

    @Test
    fun communityKeepsDataPresentationAndSmallUiFilesSeparated() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/community")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })

        root.resolve("ui").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                assertTrue("${file.name} is too large", file.readLines().size <= 300)
            }
    }

    @Test
    fun communityUsesCorrectOAuthProfileQueryAndPerSteamIdCache() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/data/SteamCommunityService.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/data/SteamCommunityCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/presentation/SteamCommunityViewModel.kt"
        ).readText()

        assertTrue(service.contains("\"steamids\" to account.steamId"))
        assertTrue(service.contains("failures.containsAll(STEAM_COMMUNITY_CORE_SECTIONS)"))
        assertTrue(cache.contains("key(snapshot.accountSteamId)"))
        assertTrue(cache.contains("it.accountSteamId == accountSteamId"))
        assertTrue(viewModel.contains("activeAccount?.steamId == account.steamId"))
        assertTrue(viewModel.contains("requestGeneration == generation"))
        assertTrue(viewModel.contains("sessionResolver.resolveOrKeep"))
    }

    @Test
    fun communityLazyColumnUsesUniqueExplicitItemKeys() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityContent.kt"
        ).readText()
        val keys = Regex("item\\(key = \\\"([^\\\"]+)\\\"\\)")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .toList()

        assertEquals(
            "Every explicit Community LazyColumn key must be unique",
            keys.distinct(),
            keys
        )
    }

    @Test
    fun communityHeaderAndPrimaryCardsAdaptToLargeText() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityScreen.kt"
        ).readText()
        val profile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityProfileContent.kt"
        ).readText()
        val unlock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityUnlockContent.kt"
        ).readText()
        val actions = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityUnlockActions.kt"
        ).readText()
        val budgetSheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityBudgetGamesSheet.kt"
        ).readText()

        assertTrue(screen.contains("compact = true"))
        assertTrue(profile.contains("BoxWithConstraints"))
        assertTrue(profile.contains("fontScale > 1.15f"))
        assertFalse(profile.contains("private fun CommunityMetric("))
        assertTrue(unlock.contains("steam_community_unlock_estimated_remaining"))
        assertTrue(actions.contains("stackActions"))
        assertTrue(actions.contains("fontScale > 1.10f"))
        assertTrue(budgetSheet.contains("MonicaModalBottomSheet("))
        assertTrue(budgetSheet.contains("navigationBarsPadding()"))
        assertTrue(budgetSheet.contains("heightIn(min = 88.dp)"))
        assertTrue(budgetSheet.contains("game.inWishlist"))
        assertTrue(budgetSheet.contains("steam_community_unlock_overage"))
    }

    @Test
    fun unknownRestrictionStillShowsRegionalSpendEstimate() {
        val unlock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityUnlockContent.kt"
        ).readText()

        assertTrue(unlock.contains("steam_community_unlock_estimated_remaining"))
        assertTrue(unlock.contains("remainingAmount(progress)"))
        assertTrue(unlock.contains("shouldShowCommunitySpendEstimate(progress.status)"))
    }

    @Test
    fun badgesUseLiveArtworkAndOpenAnInAppDetailSheet() {
        val activityContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityActivityContent.kt"
        ).readText()
        val content = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityContent.kt"
        ).readText()
        val images = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityImages.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/data/SteamCommunityService.kt"
        ).readText()

        assertTrue(activityContent.contains("onBadgeClick"))
        assertTrue(activityContent.contains("Card("))
        assertTrue(activityContent.contains("onClick = { onBadgeClick(badge) }"))
        assertTrue(activityContent.contains("CommunityBadgeIcon("))
        assertTrue(content.contains("SteamCommunityBadgeDetailSheet("))
        assertTrue(content.contains("selectedBadge"))
        assertTrue(images.contains("internal fun CommunityBadgeIcon"))
        assertTrue(service.contains("/profiles/${'$'}{account.steamId}/badges/"))
        assertTrue(service.contains("mergeBadgeDetails"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
