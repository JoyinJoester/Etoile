package takagi.ru.monica.utils

data class BackupEncryptionDecision(
    val shouldEncrypt: Boolean,
    val problem: BackupEncryptionProblem? = null,
)

enum class BackupEncryptionProblem {
    ENCRYPTION_NOT_ALLOWED,
    ENCRYPTION_DISABLED,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
}

object BackupEncryptionPolicy {
    const val MIN_STEAM_CLOUD_PASSWORD_LENGTH = 8

    fun decide(
        contentScope: BackupContentScope,
        allowBackupEncryption: Boolean,
        encryptionEnabled: Boolean,
        encryptionPassword: String,
    ): BackupEncryptionDecision {
        if (contentScope != BackupContentScope.STEAM_MAFILE_ONLY) {
            return BackupEncryptionDecision(
                shouldEncrypt = allowBackupEncryption &&
                    encryptionEnabled &&
                    encryptionPassword.isNotEmpty(),
            )
        }

        val problem = when {
            !allowBackupEncryption -> BackupEncryptionProblem.ENCRYPTION_NOT_ALLOWED
            !encryptionEnabled -> BackupEncryptionProblem.ENCRYPTION_DISABLED
            encryptionPassword.isBlank() -> BackupEncryptionProblem.PASSWORD_REQUIRED
            encryptionPassword.length < MIN_STEAM_CLOUD_PASSWORD_LENGTH ->
                BackupEncryptionProblem.PASSWORD_TOO_SHORT
            else -> null
        }
        return BackupEncryptionDecision(
            shouldEncrypt = problem == null,
            problem = problem,
        )
    }
}
