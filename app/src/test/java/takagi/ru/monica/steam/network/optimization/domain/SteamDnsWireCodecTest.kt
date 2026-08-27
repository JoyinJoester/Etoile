package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDnsWireCodecTest {
    @Test
    fun buildsAQueryAndParsesValidatedPublicAnswer() {
        val query = SteamDnsWireCodec.buildAQuery(
            hostname = "steamcommunity.com",
            transactionId = 0x1234
        )
        assertEquals(
            "1234010000010000000000000e737465616d636f6d6d756e69747903636f6d0000010001",
            query.toHex()
        )

        val response = hex(
            "123481800001000100000000" +
                "0e737465616d636f6d6d756e69747903636f6d0000010001" +
                "c00c000100010000003c00046812140a"
        )

        assertEquals(
            listOf("104.18.20.10"),
            SteamDnsWireCodec.parseAResponse(
                response,
                transactionId = 0x1234,
                expectedHostname = "steamcommunity.com"
            )
        )
    }

    @Test
    fun rejectsWrongTransactionHostAndReservedAnswers() {
        val publicResponse = hex(
            "123481800001000100000000" +
                "0e737465616d636f6d6d756e69747903636f6d0000010001" +
                "c00c000100010000003c00046812140a"
        )
        val reservedResponse = hex(
            "123481800001000100000000" +
                "0e737465616d636f6d6d756e69747903636f6d0000010001" +
                "c00c000100010000003c0004c6120001"
        )

        assertTrue(
            SteamDnsWireCodec.parseAResponse(
                publicResponse,
                transactionId = 0x9999,
                expectedHostname = "steamcommunity.com"
            ).isEmpty()
        )
        assertTrue(
            SteamDnsWireCodec.isTruncatedResponse(
                hex("123482000001000000000000"),
                transactionId = 0x1234
            )
        )
        assertTrue(
            SteamDnsWireCodec.parseAResponse(
                publicResponse,
                transactionId = 0x1234,
                expectedHostname = "store.steampowered.com"
            ).isEmpty()
        )
        assertTrue(
            SteamDnsWireCodec.parseAResponse(
                reservedResponse,
                transactionId = 0x1234,
                expectedHostname = "steamcommunity.com"
            ).isEmpty()
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
