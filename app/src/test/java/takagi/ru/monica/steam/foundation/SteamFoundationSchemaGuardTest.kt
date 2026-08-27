package takagi.ru.monica.steam.foundation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFoundationSchemaGuardTest {
    @Test
    fun steamDatabaseV6KeepsEarlierMigrationsAndAddsDurableOutbox() {
        val database = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamDatabase.kt"
        ).readText()
        val account = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountEntity.kt"
        ).readText()
        val event = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamSecurityEventEntity.kt"
        ).readText()
        val dao = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamSecurityEventDao.kt"
        ).readText()
        val accountRepository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        val eventRepository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamSecurityEventRepository.kt"
        ).readText()

        assertTrue(database.contains("SteamAccountEntity::class"))
        assertTrue(database.contains("SteamSecurityEventEntity::class"))
        assertTrue(database.contains("SteamLibraryCacheEntity::class"))
        assertTrue(database.contains("SteamAchievementsCacheEntity::class"))
        assertTrue(database.contains("SteamOutboxEntity::class"))
        assertTrue(database.contains("version = 6"))
        assertTrue(database.contains(".addMigrations(migration3To4())"))
        assertTrue(database.contains(".addMigrations(migration4To5())"))
        assertTrue(database.contains(".addMigrations(migration5To6())"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN groupName TEXT"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN accentArgb INTEGER"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN note TEXT NOT NULL DEFAULT ''"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"))
        assertTrue(database.contains("ALTER TABLE steam_accounts ADD COLUMN lastHealthCheckAt INTEGER"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS steam_security_events"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS steam_library_cache"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS steam_achievements_cache"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS steam_outbox"))
        val migration3To4 = database
            .substringAfter("private fun migration3To4")
            .substringBefore("private fun migration1To2")
        assertFalse(migration3To4.contains("encryptExistingSteamRows"))

        listOf("groupName", "tagsJson", "accentArgb", "note", "pinned", "lastHealthCheckAt")
            .forEach { assertTrue(account.contains(it)) }
        assertTrue(event.contains("ForeignKey.CASCADE"))
        assertFalse(event.contains("sharedSecret"))
        assertFalse(event.contains("identitySecret"))
        assertFalse(event.contains("accessToken"))
        assertFalse(event.contains("refreshToken"))
        assertTrue(dao.contains("LIMIT :maxEvents"))
        assertTrue(dao.contains("observeRecent"))

        assertFalse(accountRepository.contains("suspend fun updateOrganization("))
        val backupRestore = accountRepository
            .substringAfter("suspend fun restoreFromBackup(")
            .substringBefore("suspend fun markHealthChecked(")
        assertTrue(backupRestore.contains("groupName = groupName"))
        assertTrue(backupRestore.contains("tagsJson = encrypt(SteamAccountTags.encode(tags))"))
        assertTrue(backupRestore.contains("accentArgb = accentArgb"))
        assertTrue(backupRestore.contains("note = encrypt(note.trim())"))
        assertTrue(backupRestore.contains("pinned = pinned"))

        val healthUpdate = accountRepository
            .substringAfter("suspend fun markHealthChecked(")
            .substringBefore("suspend fun updateSessionTokens(")
        assertTrue(healthUpdate.contains("existing.copy(lastHealthCheckAt = checkedAt)"))
        assertFalse(healthUpdate.contains("sharedSecret ="))

        val eventRecord = eventRepository
            .substringAfter("suspend fun record(")
            .substringBefore("suspend fun clear()")
        assertTrue(eventRecord.contains("SteamSecurityEventSanitizer.sanitize(summary)"))
        assertTrue(eventRecord.contains("securityManager.encryptDataLegacyCompat(safeSummary)"))
        assertFalse(eventRecord.contains("sharedSecret"))
        assertFalse(eventRecord.contains("accessToken"))
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
