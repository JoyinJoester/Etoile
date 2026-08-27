package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRule

class SteamHostsDiagnosticsRunnerTest {
    @Test
    fun testsEveryAddressAndStreamsResultsInRuleOrder() = runBlocking {
        val streamed = mutableListOf<SteamHostProbeResult>()
        val runner = SteamHostsDiagnosticsRunner(
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 42L,
                    httpStatusCode = 403
                )
            }
        )

        val results = runner.run(
            rules = listOf(
                SteamHostsRule(
                    hostname = "store.steampowered.com",
                    addresses = listOf("23.45.67.89", "23.45.67.90")
                )
            ),
            onResult = streamed::add
        )

        assertEquals(2, results.size)
        assertEquals(results, streamed)
        assertTrue(results.all(SteamHostProbeResult::isAvailable))
        assertEquals(
            listOf("23.45.67.89", "23.45.67.90"),
            results.map { it.target.address }
        )
    }
}
