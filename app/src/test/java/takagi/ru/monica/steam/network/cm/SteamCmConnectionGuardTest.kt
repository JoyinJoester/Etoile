package takagi.ru.monica.steam.network.cm

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCmConnectionGuardTest {
    @Test
    fun productionGatewayUsesTheSharedPersistentPool() {
        val client = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/cm/SteamCmClient.kt"
        ).readText()
        val pool = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/cm/SteamCmConnectionPool.kt"
        ).readText()
        val connection = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/cm/SteamCmPersistentConnection.kt"
        ).readText()

        assertTrue(client.contains("SteamCmRuntime.pool"))
        assertTrue(client.contains("SteamCmConnectionPool"))
        assertTrue(pool.contains("steamCmAccountKey(account)"))
        assertTrue(client.contains("accountKeyResolver"))
        assertTrue(pool.contains("webLogonToken"))
        assertTrue(pool.contains("catch (error: SteamCmResponseTimeoutException)"))
        assertTrue(pool.contains("throw error"))
        assertTrue(connection.contains("eventSink"))
        assertTrue(connection.contains("nextJobId"))
        assertFalse(client.contains("SteamCmWebSocketExchange"))
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
