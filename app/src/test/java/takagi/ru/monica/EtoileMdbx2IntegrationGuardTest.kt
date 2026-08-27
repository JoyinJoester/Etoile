package takagi.ru.monica

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtoileMdbx2IntegrationGuardTest {

    @Test
    fun standaloneActivityRoutesAllMdbxSourcesThroughTheDualEngineFactory() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()

        assertTrue(source.contains("MdbxRepositoryFactory.create("))
        assertFalse(source.contains("val mdbxRepository: MdbxRepository = remember") &&
            source.contains("MdbxVaultStore("))
        assertTrue(source.contains("MDBX_ONEDRIVE_CREATE"))
        assertTrue(source.contains("MDBX_ONEDRIVE_OPEN"))
        assertTrue(source.contains("MdbxOneDriveCreateScreen("))
        assertTrue(source.contains("MdbxOneDriveOpenScreen("))
        assertTrue(source.contains("oneDriveEnabled = true"))
    }

    @Test
    fun standaloneBuildPackagesTheOneDriveRuntimeAndRedirectActivity() {
        val build = projectFile("app/build.gradle").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val config = projectFile("app/src/main/res/raw/onedrive_msal_config.json").readText()
        val quote = 34.toChar()

        assertTrue(build.contains("implementation 'com.microsoft.identity.client:msal:8.3.2'"))
        assertFalse(build.contains("compileOnly 'com.microsoft.identity.client:msal:8.3.2'"))
        assertTrue(manifest.contains("com.microsoft.identity.client.BrowserTabActivity"))
        assertTrue(manifest.contains("android:scheme=$quote" + "msauth$quote"))
        assertTrue(manifest.contains("android:host=$quote" + "takagi.ru.monica$quote"))
        assertTrue(config.contains("$quote" + "pii_enabled$quote: false"))
        assertTrue(config.contains("$quote" + "logcat_enabled$quote: false"))
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
