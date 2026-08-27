package takagi.ru.monica.steam.network.cm

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamCmProtocolTest {
    @Test
    fun encodesWebLogonAndUnifiedServiceHeaders() {
        val loginBody = SteamCmProtocol.webLogonBody("web-token")
        val loginFields = SteamProtoReader(loginBody).parse()

        assertEquals(SteamCmProtocol.WEB_PROTOCOL_VERSION, loginFields[1]?.asLong)
        assertEquals(SteamCmProtocol.WEB_CLIENT_OS_TYPE, loginFields[7]?.asLong)
        assertEquals(4, loginFields[32]?.asInt)
        assertEquals(2, loginFields[33]?.asInt)
        assertEquals("anonymous", loginFields[80]?.asString)
        assertEquals("web-token", loginFields[103]?.asString)

        val encoded = SteamCmProtocol.encodeMessage(
            eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_CALL_FROM_CLIENT,
            steamId = ACCOUNT_STEAM_ID,
            sessionId = 42,
            body = byteArrayOf(1, 2, 3),
            jobIdSource = 7L,
            targetJobName = "FriendMessages.SendMessage#1"
        )
        val decoded = SteamCmProtocol.decodeMessages(encoded).single()

        assertEquals(SteamCmProtocol.EMSG_SERVICE_METHOD_CALL_FROM_CLIENT, decoded.eMsg)
        assertEquals(ACCOUNT_STEAM_ID, decoded.header.steamId)
        assertEquals(42, decoded.header.sessionId)
        assertEquals(7L, decoded.header.jobIdSource)
        assertEquals(SteamCmProtocol.JOB_ID_NONE, decoded.header.jobIdTarget)
        assertEquals("FriendMessages.SendMessage#1", decoded.header.targetJobName)
        assertTrue(decoded.body.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun expandsGzippedMultiMessages() {
        val first = SteamCmProtocol.encodeMessage(
            eMsg = SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE,
            steamId = ACCOUNT_STEAM_ID,
            sessionId = 5,
            body = SteamProtoWriter().apply { writeVarint(1, 1L) }.toByteArray()
        )
        val second = SteamCmProtocol.encodeMessage(
            eMsg = SteamCmProtocol.EMSG_CLIENT_EMOTICON_LIST,
            steamId = ACCOUNT_STEAM_ID,
            sessionId = 5,
            body = ByteArray(0)
        )
        val unpacked = ByteBuffer.allocate(8 + first.size + second.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(first.size)
            .put(first)
            .putInt(second.size)
            .put(second)
            .array()
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(unpacked) }
            output.toByteArray()
        }
        val multiBody = SteamProtoWriter().apply {
            writeVarint(1, unpacked.size.toLong())
            writeBytes(2, compressed)
        }.toByteArray()
        val multi = SteamCmProtocol.encodeMessage(
            eMsg = SteamCmProtocol.EMSG_MULTI,
            steamId = ACCOUNT_STEAM_ID,
            sessionId = 5,
            body = multiBody
        )

        assertEquals(
            listOf(
                SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE,
                SteamCmProtocol.EMSG_CLIENT_EMOTICON_LIST
            ),
            SteamCmProtocol.decodeMessages(multi).map(SteamCmEnvelope::eMsg)
        )
    }

    private companion object {
        const val ACCOUNT_STEAM_ID = 76_561_198_000_000_001L
    }
}
