package takagi.ru.monica.steam.network.optimization.domain

import java.io.ByteArrayOutputStream
import java.net.InetAddress

internal object SteamDnsWireCodec {
    fun isTruncatedResponse(message: ByteArray, transactionId: Int): Boolean {
        if (message.size < 4) return false
        return message.u16(0) == transactionId &&
            message.u16(2) and RESPONSE_FLAG != 0 &&
            message.u16(2) and TRUNCATED_FLAG != 0
    }

    fun buildAQuery(hostname: String, transactionId: Int): ByteArray {
        val host = SteamHostsRuleParser.normalizeHostname(hostname)
        require(host.isNotEmpty())
        val output = ByteArrayOutputStream()
        output.u16(transactionId)
        output.u16(0x0100)
        output.u16(1)
        repeat(3) { output.u16(0) }
        host.split('.').forEach { label ->
            val bytes = label.encodeToByteArray()
            require(bytes.size in 1..63)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.u16(A_RECORD)
        output.u16(IN_CLASS)
        return output.toByteArray()
    }

    fun parseAResponse(
        message: ByteArray,
        transactionId: Int,
        expectedHostname: String
    ): List<String> = runCatching {
        if (message.size < HEADER_BYTES) return emptyList()
        val responseId = message.u16(0)
        val flags = message.u16(2)
        val questionCount = message.u16(4)
        val answerCount = message.u16(6)
        if (
            responseId != transactionId || flags and RESPONSE_FLAG == 0 ||
            flags and TRUNCATED_FLAG != 0 || flags and RESPONSE_CODE_MASK != 0 ||
            questionCount != 1
        ) {
            return emptyList()
        }
        var offset = HEADER_BYTES
        val question = readName(message, offset)
        offset = question.next
        if (offset + 4 > message.size) return emptyList()
        val questionType = message.u16(offset)
        val questionClass = message.u16(offset + 2)
        offset += 4
        if (
            question.value != SteamHostsRuleParser.normalizeHostname(expectedHostname) ||
            questionType != A_RECORD || questionClass != IN_CLASS
        ) {
            return emptyList()
        }

        buildList {
            repeat(answerCount) {
                val owner = readName(message, offset)
                offset = owner.next
                if (offset + 10 > message.size) error("Truncated DNS answer")
                val type = message.u16(offset)
                val recordClass = message.u16(offset + 2)
                val dataLength = message.u16(offset + 8)
                val dataOffset = offset + 10
                val dataEnd = dataOffset + dataLength
                if (dataEnd > message.size) error("Truncated DNS data")
                if (type == A_RECORD && recordClass == IN_CLASS && dataLength == 4) {
                    val address = InetAddress.getByAddress(
                        message.copyOfRange(dataOffset, dataEnd)
                    )
                    if (SteamHostsRuleParser.isUsableAddress(address)) {
                        address.hostAddress?.let(::add)
                    }
                }
                offset = dataEnd
            }
        }.distinct()
    }.getOrDefault(emptyList())

    private data class NameResult(val value: String, val next: Int)

    private fun readName(data: ByteArray, start: Int): NameResult {
        var cursor = start
        var next = -1
        var jumps = 0
        val labels = mutableListOf<String>()
        while (true) {
            if (cursor >= data.size || jumps++ > 64) error("Invalid DNS name")
            val length = data[cursor].toInt() and 0xff
            if (length == 0) {
                if (next < 0) next = cursor + 1
                break
            }
            if (length and 0xc0 == 0xc0) {
                if (cursor + 1 >= data.size) error("Invalid DNS pointer")
                val pointer = ((length and 0x3f) shl 8) or
                    (data[cursor + 1].toInt() and 0xff)
                if (next < 0) next = cursor + 2
                cursor = pointer
                continue
            }
            if (length > 63 || cursor + 1 + length > data.size) error("Invalid DNS label")
            labels += data.copyOfRange(cursor + 1, cursor + 1 + length).decodeToString()
            cursor += 1 + length
        }
        return NameResult(
            SteamHostsRuleParser.normalizeHostname(labels.joinToString(".")),
            next
        )
    }

    private fun ByteArray.u16(offset: Int): Int {
        if (offset + 1 >= size) error("Truncated DNS integer")
        return ((this[offset].toInt() and 0xff) shl 8) or
            (this[offset + 1].toInt() and 0xff)
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private const val HEADER_BYTES = 12
    private const val A_RECORD = 1
    private const val IN_CLASS = 1
    private const val RESPONSE_FLAG = 0x8000
    private const val TRUNCATED_FLAG = 0x0200
    private const val RESPONSE_CODE_MASK = 0x000f
}
