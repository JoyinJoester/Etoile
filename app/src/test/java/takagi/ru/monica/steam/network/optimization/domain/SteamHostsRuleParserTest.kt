package takagi.ru.monica.steam.network.optimization.domain

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamHostsRuleParserTest {
    @Test
    fun parsesCommentsIpv4Ipv6AndMultipleHostnames() {
        val result = SteamHostsRuleParser.parse(
            """
            # Etoile custom mappings
            23.45.67.89 store.steampowered.com cdn.steamstatic.com # preferred edge
            2606:4700:4700::1111 steamcommunity.com
            23.45.67.89 STORE.STEAMPOWERED.COM.
            """.trimIndent()
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(3, result.hostCount)
        assertEquals(
            SteamHostsRule(
                hostname = "store.steampowered.com",
                addresses = listOf("23.45.67.89")
            ),
            result.rules.first()
        )
        assertEquals(
            listOf("23.45.67.89"),
            result.addresses.getValue("store.steampowered.com")
                .map(InetAddress::getHostAddress)
        )
        assertEquals(
            listOf("23.45.67.89"),
            result.addresses.getValue("cdn.steamstatic.com")
                .map(InetAddress::getHostAddress)
        )
        assertEquals(1, result.addresses.getValue("steamcommunity.com").size)
    }

    @Test
    fun reportsLineSpecificErrorsAndNeverAcceptsPartialRules() {
        val result = SteamHostsRuleParser.parse(
            """
            not-an-ip store.steampowered.com
            23.45.67.89
            23.45.67.89 bad_host
            127.0.0.1 steamcommunity.com
            """.trimIndent()
        )

        assertEquals(
            listOf(
                SteamHostsRuleError(1, SteamHostsRuleErrorReason.INVALID_IP),
                SteamHostsRuleError(2, SteamHostsRuleErrorReason.INVALID_FORMAT),
                SteamHostsRuleError(3, SteamHostsRuleErrorReason.INVALID_HOSTNAME),
                SteamHostsRuleError(4, SteamHostsRuleErrorReason.UNUSABLE_ADDRESS)
            ),
            result.errors
        )
        assertTrue(result.addresses.isEmpty())
    }

    @Test
    fun rejectsReservedDocumentationAndCommonFakeIpRanges() {
        listOf(
            "100.64.0.1",
            "192.0.2.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "2001:db8::1"
        ).forEach { address ->
            val result = SteamHostsRuleParser.parse("$address store.steampowered.com")
            assertEquals(
                address,
                SteamHostsRuleErrorReason.UNUSABLE_ADDRESS,
                result.errors.single().reason
            )
        }
    }
}
