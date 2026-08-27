package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEncryptionPolicyTest {
    @Test
    fun steamCloudBackupRejectsDisabledEncryption() {
        val decision = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.STEAM_MAFILE_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = false,
            encryptionPassword = "strong-password",
        )

        assertFalse(decision.shouldEncrypt)
        assertEquals(BackupEncryptionProblem.ENCRYPTION_DISABLED, decision.problem)
    }

    @Test
    fun steamCloudBackupRejectsMissingOrShortPasswords() {
        val missing = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.STEAM_MAFILE_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = true,
            encryptionPassword = "",
        )
        val short = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.STEAM_MAFILE_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = true,
            encryptionPassword = "1234567",
        )

        assertEquals(BackupEncryptionProblem.PASSWORD_REQUIRED, missing.problem)
        assertEquals(BackupEncryptionProblem.PASSWORD_TOO_SHORT, short.problem)
    }

    @Test
    fun steamCloudBackupRequiresTheEncryptedOutputPath() {
        val decision = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.STEAM_MAFILE_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = true,
            encryptionPassword = "12345678",
        )

        assertTrue(decision.shouldEncrypt)
        assertNull(decision.problem)
    }

    @Test
    fun sharedMonicaBackupKeepsItsExistingOptionalEncryptionBehavior() {
        val plain = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.MONICA_LOCAL_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = false,
            encryptionPassword = "",
        )
        val encrypted = BackupEncryptionPolicy.decide(
            contentScope = BackupContentScope.MONICA_LOCAL_ONLY,
            allowBackupEncryption = true,
            encryptionEnabled = true,
            encryptionPassword = "legacy",
        )

        assertFalse(plain.shouldEncrypt)
        assertNull(plain.problem)
        assertTrue(encrypted.shouldEncrypt)
        assertNull(encrypted.problem)
    }
}
