package takagi.ru.monica.repository

import android.content.Context
import java.util.UUID
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

object MdbxRepositoryFactory {
    fun create(
        context: Context,
        database: PasswordDatabase,
        securityManager: SecurityManager
    ): MdbxRepository {
        val appContext = context.applicationContext
        val databaseDao = database.localMdbxDatabaseDao()
        val legacy = MdbxVaultStore(
            context = appContext,
            databaseDao = databaseDao,
            securityManager = securityManager,
            remoteSourceDao = database.mdbxRemoteSourceDao(),
            passwordEntryDao = database.passwordEntryDao(),
            secureItemDao = database.secureItemDao(),
            customFieldDao = database.customFieldDao()
        )
        val rust = Mdbx2Repository(
            context = appContext,
            databaseDao = databaseDao,
            securityManager = securityManager,
            passwordEntryDao = database.passwordEntryDao(),
            secureItemDao = database.secureItemDao(),
            customFieldDao = database.customFieldDao()
        )
        return MdbxRepositoryRouter(databaseDao, legacy, rust)
    }
}

fun mdbxPasswordObjectId(entry: PasswordEntry): String =
    entry.replicaGroupId
        ?.takeIf { it.startsWith("password:") && it.length > "password:".length }
        ?: "password:${entry.id}"

internal fun mdbx2PhysicalEntryId(vaultId: String, logicalEntryId: String): String =
    UUID.nameUUIDFromBytes(
        "monica-entry:$vaultId:$logicalEntryId".toByteArray(Charsets.UTF_8)
    ).toString()

internal fun mdbx2PhysicalAttachmentId(vaultId: String, logicalAttachmentId: String): String =
    UUID.nameUUIDFromBytes(
        "monica-attachment:$vaultId:$logicalAttachmentId".toByteArray(Charsets.UTF_8)
    ).toString()
