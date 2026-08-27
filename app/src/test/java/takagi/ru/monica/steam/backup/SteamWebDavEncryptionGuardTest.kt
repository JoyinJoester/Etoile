package takagi.ru.monica.steam.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWebDavEncryptionGuardTest {
    @Test
    fun steamOnlyUploadIsWiredThroughTheMandatoryEncryptionPolicy() {
        val helper = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt"
        ).readText().replace("\r\n", "\n")
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/WebDavBackupScreen.kt"
        ).readText()
        val worker = projectFile(
            "app/src/main/java/takagi/ru/monica/workers/AutoBackupWorker.kt"
        ).readText()

        assertTrue(helper.contains("val encryptionDecision = BackupEncryptionPolicy.decide("))
        assertTrue(helper.contains("encryptionDecision.problem?.let"))
        assertTrue(helper.contains("val shouldEncryptBackup = encryptionDecision.shouldEncrypt"))
        assertTrue(screen.contains("steamEncryptionReady"))
        assertTrue(screen.contains("webdav_steam_mafile_encryption_required"))
        assertTrue(screen.contains("scheduleAutoBackup(steamMaFileOnly = steamMaFileOnly)"))
        assertTrue(worker.contains("!webDavHelper.isSteamMaFileCloudBackupReady()"))
        assertTrue(worker.contains("steam_backup_encryption_required"))
    }

    @Test
    fun restoreStillDetectsEncryptedAndLegacyPlaintextBackups() {
        val helper = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(helper.contains("val isEncrypted = EncryptionHelper.isEncryptedFile(backupFile)"))
        assertTrue(helper.contains("val zipFile = if (isEncrypted)"))
        assertTrue(helper.contains("} else {\n                backupFile"))
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
