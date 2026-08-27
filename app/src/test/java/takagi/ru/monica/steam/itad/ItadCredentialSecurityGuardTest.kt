package takagi.ru.monica.steam.itad

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItadCredentialSecurityGuardTest {
    @Test
    fun apiKeyUsesDedicatedEncryptedPreferencesWithoutLogging() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/itad/data/ItadCredentialStore.kt"
        ).readText()

        assertTrue(source.contains("EncryptedSharedPreferences.create("))
        assertTrue(source.contains("MasterKey.KeyScheme.AES256_GCM"))
        assertTrue(source.contains("monica_itad_credentials"))
        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("println("))
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
