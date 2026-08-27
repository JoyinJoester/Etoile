package takagi.ru.monica.steam.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamMdbx2RoutingGuardTest {

    @Test
    fun everyStandaloneSteamAccountEntryPointUsesTheDualEngineFactory() {
        val sources = listOf(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt",
            "app/src/main/java/takagi/ru/monica/steam/scanner/ui/SteamQrScannerScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).map(::projectFile).map(File::readText)

        sources.forEach { source ->
            assertTrue(source.contains("MdbxRepositoryFactory.create("))
            assertFalse(source.contains("MdbxVaultStore("))
        }
    }

    @Test
    fun OneDriveVaultsAreVisibleToSteamAccountSelectors() {
        val repository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt"
        ).readText()
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(repository.contains("MdbxSourceType.REMOTE_ONEDRIVE"))
        assertTrue(tokenScreen.contains("MdbxSourceType.REMOTE_ONEDRIVE"))
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            candidates += File(directory, relativePath)
            directory = directory.parentFile
        }
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to find project file: $relativePath")
    }
}
