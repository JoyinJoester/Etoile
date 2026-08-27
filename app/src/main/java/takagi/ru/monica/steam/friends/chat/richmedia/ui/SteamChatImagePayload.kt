package takagi.ru.monica.steam.friends.chat.richmedia.ui

private val pngSignature = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
)

/** Detects APNG by parsing PNG chunks instead of trusting a file extension or MIME type. */
internal fun isAnimatedPng(payload: ByteArray): Boolean {
    if (payload.size < pngSignature.size + PNG_CHUNK_OVERHEAD) return false
    if (pngSignature.indices.any { payload[it] != pngSignature[it] }) return false

    var offset = pngSignature.size
    while (offset + PNG_CHUNK_OVERHEAD <= payload.size) {
        val dataLength = readPngUInt32(payload, offset)
        if (dataLength > Int.MAX_VALUE.toLong()) return false

        val typeOffset = offset + PNG_LENGTH_BYTES
        val chunkEnd = offset.toLong() + PNG_CHUNK_OVERHEAD + dataLength
        if (chunkEnd > payload.size.toLong()) return false

        when {
            payload.hasChunkType(typeOffset, "acTL") -> return true
            payload.hasChunkType(typeOffset, "IDAT") ||
                payload.hasChunkType(typeOffset, "IEND") -> return false
        }
        offset = chunkEnd.toInt()
    }
    return false
}

/** GIF files may be static or animated; ImageDecoder can safely decide which. */
internal fun isGif(payload: ByteArray): Boolean =
    payload.size >= 6 &&
        (payload.copyOfRange(0, 6).contentEquals("GIF87a".encodeToByteArray()) ||
            payload.copyOfRange(0, 6).contentEquals("GIF89a".encodeToByteArray()))

/** Detects animated WebP without relying on the HTTP Content-Type header. */
internal fun isAnimatedWebp(payload: ByteArray): Boolean {
    if (payload.size < 16) return false
    if (!payload.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray())) return false
    if (!payload.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray())) return false
    var offset = 12
    while (offset + 8 <= payload.size) {
        val type = payload.copyOfRange(offset, offset + 4)
        val size = readLittleEndianUInt32(payload, offset + 4)
        val end = offset.toLong() + 8L + size + (size and 1L)
        if (end > payload.size.toLong()) return false
        if (type.contentEquals("ANIM".encodeToByteArray()) ||
            type.contentEquals("ANMF".encodeToByteArray())
        ) return true
        offset = end.toInt()
    }
    return false
}

internal fun isAnimatedSteamImage(payload: ByteArray): Boolean =
    isAnimatedPng(payload) || isGif(payload) || isAnimatedWebp(payload)

private fun readLittleEndianUInt32(payload: ByteArray, offset: Int): Long =
    (payload[offset].toLong() and 0xffL) or
        ((payload[offset + 1].toLong() and 0xffL) shl 8) or
        ((payload[offset + 2].toLong() and 0xffL) shl 16) or
        ((payload[offset + 3].toLong() and 0xffL) shl 24)

private fun readPngUInt32(payload: ByteArray, offset: Int): Long =
    ((payload[offset].toLong() and 0xffL) shl 24) or
        ((payload[offset + 1].toLong() and 0xffL) shl 16) or
        ((payload[offset + 2].toLong() and 0xffL) shl 8) or
        (payload[offset + 3].toLong() and 0xffL)

private fun ByteArray.hasChunkType(offset: Int, expected: String): Boolean =
    expected.indices.all { index -> this[offset + index] == expected[index].code.toByte() }

private const val PNG_LENGTH_BYTES = 4
private const val PNG_CHUNK_OVERHEAD = 12
