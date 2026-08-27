package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginOnlyCodeCrashTest {
    @Test
    fun viewModelDoesNotGenerateCodesForAccountsWithoutAuthenticatorSecrets() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()
        val updateForAccounts = source
            .substringAfter("private fun updateForAccounts(")
            .substringBefore("private fun updateCodeTick(")
        val updateCodeTick = source
            .substringAfter("private fun updateCodeTick(")
            .substringBefore("private fun secondsRemaining(")

        assertTrue(updateForAccounts.contains("takeIf { it.hasAuthenticatorCode }"))
        assertTrue(updateCodeTick.contains("takeIf { it.hasAuthenticatorCode }"))
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
