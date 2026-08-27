package takagi.ru.monica.steam.outbox.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOutboxDatabaseGuardTest {
    @Test
    fun databaseMigrationAndProductionStoreKeepOutboxDurableAndProtected() {
        val database = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamDatabase.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/outbox/data/SteamOutboxStore.kt"
        ).readText()
        val chat = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatViewModel.kt"
        ).readText()
        val recovery = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatOutboxRecovery.kt"
        ).readText()

        assertTrue(database.contains("version = 6"))
        assertTrue(database.contains("migration5To6"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS steam_outbox"))
        assertTrue(database.contains("steamOutboxDao"))
        assertTrue(store.contains("encryptDataLegacyCompat"))
        assertTrue(store.contains("protectedAccountSteamId"))
        assertTrue(store.contains("dedupeKey"))
        assertTrue(recovery.contains("recoverPendingSteamChatOutbox"))
        assertTrue(chat.contains("outbox = outbox"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
