package takagi.ru.monica.steam.library.analytics.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameDistributionUiGuardTest {
    @Test
    fun barsSupportTapLongPressHapticsAndM3eDetails() {
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/analytics/ui/" +
                "SteamGameDistributionCard.kt"
        ).readText().replace("\r\n", "\n")
        val sheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/analytics/ui/" +
                "SteamGameDistributionDetailSheet.kt"
        ).readText()

        assertTrue(card.contains("selectedBucket"))
        assertTrue(card.contains("combinedClickable("))
        assertTrue(card.contains("HapticFeedbackType.LongPress"))
        assertTrue(card.contains("performHapticFeedback"))
        assertTrue(card.contains("SteamGameDistributionDetailSheet("))
        assertTrue(card.contains("contentDescription = accessibilityLabel"))
        assertTrue(sheet.contains("MonicaModalBottomSheet("))
        assertTrue(sheet.contains("LazyColumn("))
        assertTrue(sheet.contains("SteamDistributionGameIcon("))
        assertTrue(sheet.contains("bucket.games"))
    }

    @Test
    fun longPressIndicationIsScopedToTheRenderedBar() {
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/analytics/ui/" +
                "SteamGameDistributionCard.kt"
        ).readText().replace("\r\n", "\n")

        // The interaction must live on the bar itself; putting it on the full-height
        // column paints the pressed indication over the labels and empty chart space.
        assertFalse(card.contains(".fillMaxHeight()\n                    .clip(RoundedCornerShape(14.dp))"))
        assertTrue(
            card.contains(
                ".height(barHeight)\n" +
                    "                        .clip(CircleShape)\n" +
                    "                        .background("
            )
        )
        assertTrue(
            card.contains(
                ".background(\n" +
                    "                            if (isHighest) MaterialTheme.colorScheme.primary\n" +
                    "                            else MaterialTheme.colorScheme.secondaryContainer\n" +
                    "                        )\n" +
                    "                        .semantics { contentDescription = accessibilityLabel }\n" +
                    "                        .combinedClickable("
            )
        )
    }

    @Test
    fun detailRowsUseTheExistingStoreNavigationCallback() {
        val sheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/analytics/ui/" +
                "SteamGameDistributionDetailSheet.kt"
        ).readText()
        val libraryScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()

        assertTrue(sheet.contains("onOpenGame(game.appId)"))
        assertTrue(sheet.contains("onClickLabel = openStoreLabel"))
        assertTrue(libraryScreen.contains("onOpenGame = onOpenStoreApp"))
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
