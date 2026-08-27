package takagi.ru.monica.steam.friends.chat.richmedia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatImagePayloadTest {
    @Test
    fun detectsAnimatedPngFromItsActlChunk() {
        assertTrue(isAnimatedPng(pngWithChunk("acTL")))
    }

    @Test
    fun doesNotTreatOrdinaryPngAsAnimated() {
        assertFalse(isAnimatedPng(pngWithChunk("IDAT")))
    }

    @Test
    fun rejectsActlTextOutsideAPngContainer() {
        assertFalse(isAnimatedPng("not-a-png-acTL".encodeToByteArray()))
    }

    @Test
    fun recognizesGifHeadersForPlatformAnimationDecoding() {
        assertTrue(isGif("GIF89a".encodeToByteArray()))
        assertFalse(isGif("PNGxxx".encodeToByteArray()))
    }

    @Test
    fun recognizesAnimatedWebpChunks() {
        val payload = riffWebp("ANIM")
        assertTrue(isAnimatedWebp(payload))
        assertTrue(isAnimatedSteamImage(payload))
    }

    private fun pngWithChunk(chunkName: String): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x00,
        chunkName[0].code.toByte(),
        chunkName[1].code.toByte(),
        chunkName[2].code.toByte(),
        chunkName[3].code.toByte(),
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00
    )

    private fun riffWebp(chunkName: String): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0x08, 0x00, 0x00, 0x00,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
        chunkName[0].code.toByte(), chunkName[1].code.toByte(),
        chunkName[2].code.toByte(), chunkName[3].code.toByte(),
        0x00, 0x00, 0x00, 0x00
    )
}
