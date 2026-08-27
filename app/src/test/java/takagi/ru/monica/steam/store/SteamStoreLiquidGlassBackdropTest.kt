package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreLiquidGlassBackdropTest {
    @Test
    fun storeDestinationsPaintAnOpaqueSemanticBackdropForLiquidGlass() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val destinationHost = source
            .substringAfter("targetState = storeDestination,")
            .substringBefore("transitionSpec =")

        assertTrue(destinationHost.contains(".fillMaxSize()"))
        assertTrue(
            destinationHost.contains(
                ".background(MaterialTheme.colorScheme.background)"
            )
        )
        assertFalse(destinationHost.contains("Color.Black"))
        assertFalse(destinationHost.contains("Color.White"))
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
