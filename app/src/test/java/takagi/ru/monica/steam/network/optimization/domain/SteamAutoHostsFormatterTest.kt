package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAutoHostsFormatterTest {
    @Test
    fun generatedRoutesArePlacedBeforePreservedManualRules() {
        val merged = SteamAutoHostsFormatter.merge(
            existingText = "23.45.67.89 example.steampowered.com # manual",
            result = completeResult(),
            scannedAtMillis = 123_456L
        )

        assertTrue(merged.startsWith(SteamAutoHostsFormatter.BEGIN_MARKER))
        assertTrue(merged.contains("10.0.0.1 store.steampowered.com"))
        assertTrue(merged.contains("23.45.67.89 example.steampowered.com # manual"))
        assertTrue(
            merged.indexOf("10.0.0.1 store.steampowered.com") <
                merged.indexOf("23.45.67.89 example.steampowered.com")
        )

        val summary = requireNotNull(SteamAutoHostsFormatter.summary(merged))
        assertEquals(123_456L, summary.scannedAtMillis)
        assertEquals(35L, summary.averageLatencyMillis)
        assertEquals(listOf("cloudflare", "alidns"), summary.providerIds)
        assertEquals(2, summary.selectedHostCount)
        assertEquals(2, summary.totalHostCount)
        assertTrue(summary.missingHostnames.isEmpty())
    }

    @Test
    fun rescanningReplacesOnlyThePreviousGeneratedBlock() {
        val first = SteamAutoHostsFormatter.merge(
            existingText = "23.45.67.89 example.steampowered.com",
            result = completeResult(),
            scannedAtMillis = 100L
        )
        val second = SteamAutoHostsFormatter.merge(
            existingText = first,
            result = completeResult(addressSuffix = 9),
            scannedAtMillis = 200L
        )

        assertEquals(1, second.windowed(SteamAutoHostsFormatter.BEGIN_MARKER.length)
            .count { it == SteamAutoHostsFormatter.BEGIN_MARKER })
        assertFalse(second.contains("10.0.0.1 store.steampowered.com"))
        assertTrue(second.contains("10.0.0.9 store.steampowered.com"))
        assertTrue(second.contains("23.45.67.89 example.steampowered.com"))
        assertEquals(200L, SteamAutoHostsFormatter.summary(second)?.scannedAtMillis)
    }

    @Test
    fun generatedRoutesCanBeReusedByTheNextScan() {
        val hostsText = SteamAutoHostsFormatter.merge(
            existingText = "",
            result = completeResult(),
            scannedAtMillis = 123_456L
        )

        val routes = SteamAutoHostsFormatter.routes(hostsText)

        assertEquals(2, routes.size)
        assertEquals("10.0.0.1", routes.first().address)
        assertEquals("store.steampowered.com", routes.first().hostname)
        assertEquals(listOf("cloudflare"), routes.first().providerIds)
        assertEquals(30L, routes.first().latencyMillis)
    }

    @Test
    fun partialCoveragePersistsTheMissingHostList() {
        val complete = completeResult()
        val result = complete.copy(
            targetHostnames = listOf(
                "store.steampowered.com",
                "steamcommunity.com",
                "api.steampowered.com"
            ),
            selectedRoutes = complete.selectedRoutes.take(2)
        )

        val merged = SteamAutoHostsFormatter.merge("", result, scannedAtMillis = 99L)

        assertEquals(
            listOf("api.steampowered.com"),
            SteamAutoHostsFormatter.summary(merged)?.missingHostnames
        )
    }

    private fun completeResult(addressSuffix: Int = 1): SteamDnsOptimizationScanResult =
        SteamDnsOptimizationScanResult(
            targetHostnames = listOf("store.steampowered.com", "steamcommunity.com"),
            resolutions = emptyList(),
            probeResults = emptyList(),
            selectedRoutes = listOf(
                SteamDnsSelectedRoute(
                    hostname = "store.steampowered.com",
                    address = "10.0.0.$addressSuffix",
                    providerIds = listOf("cloudflare"),
                    latencyMillis = 30L,
                    httpStatusCode = 200
                ),
                SteamDnsSelectedRoute(
                    hostname = "steamcommunity.com",
                    address = "20.0.0.$addressSuffix",
                    providerIds = listOf("alidns"),
                    latencyMillis = 40L,
                    httpStatusCode = 200
                )
            )
        )
}
