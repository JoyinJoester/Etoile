package takagi.ru.monica.steam.diagnostics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCrashDiagnosticsTest {
    @Test
    fun applicationInstallsPersistentCrashHandlerBeforeActivityStartup() {
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileApplication.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/diagnostics/SteamCrashDiagnostics.kt"
        )

        assertTrue(source.exists())
        val diagnostics = source.readText()
        val installIndex = application.indexOf("SteamCrashDiagnostics.install(")
        val superIndex = application.indexOf("super.onCreate()")
        assertTrue(installIndex >= 0)
        assertTrue(superIndex >= 0)
        assertTrue(installIndex < superIndex)
        assertTrue(manifest.contains("android:name=\".EtoileApplication\""))
        assertTrue(diagnostics.contains("setDefaultUncaughtExceptionHandler"))
        assertTrue(diagnostics.contains("previousHandler?.uncaughtException"))
        assertTrue(diagnostics.contains("readLastCrash("))
        assertTrue(diagnostics.contains("MAX_CRASH_BYTES"))
        assertTrue(diagnostics.contains("renameTo("))
    }

    @Test
    fun exportsReadPersistedCrashAndExplicitLogcatBuffers() {
        val developer = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/DeveloperSettingsScreen.kt"
        ).readText()
        val steam = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/diagnostics/SteamSupportLogExporter.kt"
        ).readText()

        assertTrue(developer.contains("SteamCrashDiagnostics.readLastCrash("))
        assertTrue(developer.contains("SteamCrashDiagnostics.clear("))
        assertTrue(steam.contains("SteamCrashDiagnostics.readLastCrash("))
        listOf("\"crash\"", "\"main\"", "\"system\"").forEach { buffer ->
            assertTrue(developer.contains("-b"))
            assertTrue(developer.contains(buffer))
            assertTrue(steam.contains("-b"))
            assertTrue(steam.contains(buffer))
        }
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
