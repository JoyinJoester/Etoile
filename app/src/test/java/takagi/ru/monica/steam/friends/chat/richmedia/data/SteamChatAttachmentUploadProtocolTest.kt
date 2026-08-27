package takagi.ru.monica.steam.friends.chat.richmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatAttachmentUploadProtocolTest {
    @Test
    fun communityRequestUsesOneEncodedWebSessionForCookieAndForm() {
        val session = SteamChatUploadSession(
            sessionId = "0123456789abcdef01234567",
            encodedSteamLoginSecure = "76561198000000001%7C%7Caccess-token"
        )

        val headers = buildSteamChatCommunityHeaders(session)

        assertEquals(
            "steamLoginSecure=76561198000000001%7C%7Caccess-token; " +
                "sessionid=0123456789abcdef01234567",
            headers["Cookie"]
        )
        assertFalse(headers.names().contains("X-Requested-With"))
        assertEquals("0123456789abcdef01234567", session.sessionId)
    }

    @Test
    fun cloudHeadersMatchTheExactSteamReservation() {
        val headers = buildSteamChatCloudHeaders(
            listOf(
                "x-amz-acl" to "private",
                "Content-Type" to "image/png",
                "Host" to "steamcloud.example",
                "Content-Length" to "68",
                "Cookie" to "steam-issued-cloud-cookie",
                "Authorization" to "steam-issued-cloud-signature"
            )
        )

        assertEquals("private", headers["x-amz-acl"])
        assertEquals("image/png", headers["Content-Type"])
        assertEquals("steam-issued-cloud-cookie", headers["Cookie"])
        assertEquals("steam-issued-cloud-signature", headers["Authorization"])
        assertFalse(headers.names().contains("Host"))
        assertFalse(headers.names().contains("Content-Length"))
    }

    @Test
    fun authenticationHttpFailuresAreClassifiedForOneSessionRetry() {
        assertTrue(SteamChatUploadException.httpFailure("begin", 400).isAuthenticationFailure)
        assertTrue(SteamChatUploadException.httpFailure("begin", 401).isAuthenticationFailure)
        assertTrue(SteamChatUploadException.httpFailure("begin", 403).isAuthenticationFailure)
        assertFalse(SteamChatUploadException.httpFailure("cloud", 404).isAuthenticationFailure)
        assertEquals(
            SteamChatUploadFailure.SERVICE,
            SteamChatUploadException.cloudFailure(404).failure
        )
    }

    @Test
    fun steamBusinessErrorsKeepTheOfficialMessageAndKnownCategory() {
        val error = SteamChatUploadException.steamRejected(
            code = 112,
            message = "Limited users cannot upload images."
        )

        assertEquals("Limited users cannot upload images.", error.message)
        assertEquals(SteamChatUploadFailure.LIMITED_ACCOUNT, error.failure)
        assertFalse(error.isAuthenticationFailure)
    }
}
