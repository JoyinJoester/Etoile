package takagi.ru.monica.steam.friends.chat.richmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamChatCatalogServiceTest {
    @Test
    fun loadsOnlyOwnedEmoticonsStickersAndEffectsFromOfficialCatalogue() {
        val cm = RecordingCmGateway(
            SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeString(1, ":steamthumbsup:")
                    writeVarint(2, 1L)
                    writeVarint(3, 100L)
                    writeVarint(4, 5L)
                    writeVarint(6, 753L)
                })
                writeMessage(2, SteamProtoWriter().apply {
                    writeString(1, "Mesmer spin")
                    writeVarint(2, 1L)
                    writeVarint(4, 570L)
                    writeVarint(5, 90L)
                })
                writeMessage(3, SteamProtoWriter().apply {
                    writeString(1, "confetti")
                    writeVarint(2, 1L)
                    writeVarint(3, 80L)
                    writeBool(4, true)
                    writeVarint(5, 570L)
                })
            }.toByteArray()
        )
        val service = SteamChatCatalogService(cm)

        val catalog = service.loadCatalog(account())
        val emoticon = catalog.emoticons.single()
        val sticker = catalog.stickers.single()
        val effect = catalog.effects.single()

        assertEquals("steamthumbsup", emoticon.name)
        assertEquals(":steamthumbsup:", emoticon.messageCode)
        assertEquals("Mesmer spin", sticker.name)
        assertTrue(sticker.imageUrl.endsWith("Mesmer%20spin"))
        assertEquals("/roomeffect confetti", effect.messageCode)
        assertTrue(catalog.stickers.none { it.name == "locked-point-shop-item" })
        assertEquals(SteamCmProtocol.EMSG_CLIENT_GET_EMOTICON_LIST, cm.requestEMsg)
        assertEquals(SteamCmProtocol.EMSG_CLIENT_EMOTICON_LIST, cm.responseEMsg)
        assertTrue(requireNotNull(cm.request).isEmpty())
    }

    private class RecordingCmGateway(
        private val response: ByteArray
    ) : SteamCmGateway {
        var requestEMsg: Int? = null
        var responseEMsg: Int? = null
        var request: ByteArray? = null

        override fun callService(
            account: SteamAccount,
            method: String,
            request: ByteArray
        ): ByteArray = error("Unexpected unified service call")

        override fun exchangeClientMessage(
            account: SteamAccount,
            requestEMsg: Int,
            responseEMsg: Int,
            request: ByteArray
        ): ByteArray {
            this.requestEMsg = requestEMsg
            this.responseEMsg = responseEMsg
            this.request = request
            return response
        }
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = null,
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
