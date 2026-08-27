package takagi.ru.monica.steam.network.optimization.domain

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

enum class SteamHostsRuleErrorReason {
    INVALID_FORMAT,
    INVALID_IP,
    INVALID_HOSTNAME,
    UNUSABLE_ADDRESS
}

data class SteamHostsRuleError(
    val lineNumber: Int,
    val reason: SteamHostsRuleErrorReason
)

data class SteamHostsParseResult(
    val addresses: Map<String, List<InetAddress>>,
    val errors: List<SteamHostsRuleError>
) {
    val isValid: Boolean get() = errors.isEmpty()
    val hostCount: Int get() = addresses.size
    val rules: List<SteamHostsRule>
        get() = addresses.map { (hostname, values) ->
            SteamHostsRule(
                hostname = hostname,
                addresses = values.map(InetAddress::getHostAddress)
            )
        }
}

object SteamHostsRuleParser {
    fun parse(text: String): SteamHostsParseResult {
        val parsed = linkedMapOf<String, MutableList<InetAddress>>()
        val errors = mutableListOf<SteamHostsRuleError>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val content = rawLine.substringBefore('#').trim()
            if (content.isEmpty()) return@forEachIndexed

            val tokens = content.split(WHITESPACE).filter(String::isNotBlank)
            if (tokens.size < 2) {
                errors += SteamHostsRuleError(
                    lineNumber,
                    SteamHostsRuleErrorReason.INVALID_FORMAT
                )
                return@forEachIndexed
            }

            val address = parseNumericAddress(tokens.first())
            if (address == null) {
                errors += SteamHostsRuleError(
                    lineNumber,
                    SteamHostsRuleErrorReason.INVALID_IP
                )
                return@forEachIndexed
            }
            if (!isUsableAddress(address)) {
                errors += SteamHostsRuleError(
                    lineNumber,
                    SteamHostsRuleErrorReason.UNUSABLE_ADDRESS
                )
                return@forEachIndexed
            }

            val hostnames = tokens.drop(1).map(::normalizeHostname)
            if (hostnames.any { !isValidHostname(it) }) {
                errors += SteamHostsRuleError(
                    lineNumber,
                    SteamHostsRuleErrorReason.INVALID_HOSTNAME
                )
                return@forEachIndexed
            }

            hostnames.forEach { hostname ->
                val addresses = parsed.getOrPut(hostname) { mutableListOf() }
                if (address !in addresses) addresses += address
            }
        }

        if (errors.isNotEmpty()) {
            return SteamHostsParseResult(addresses = emptyMap(), errors = errors)
        }
        return SteamHostsParseResult(
            addresses = parsed.mapValues { (_, values) -> values.toList() },
            errors = emptyList()
        )
    }

    internal fun normalizeHostname(hostname: String): String =
        hostname.trim().trimEnd('.').lowercase(Locale.ROOT)

    internal fun isUsableAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xff }
            val a = bytes[0]
            val b = bytes[1]
            val c = bytes[2]
            return when {
                a == 0 || a == 10 || a == 127 -> false
                a == 100 && b in 64..127 -> false
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 0 && c == 0 -> false
                a == 192 && b == 0 && c == 2 -> false
                a == 192 && b == 88 && c == 99 -> false
                a == 192 && b == 168 -> false
                a == 198 && b in 18..19 -> false
                a == 198 && b == 51 && c == 100 -> false
                a == 203 && b == 0 && c == 113 -> false
                a == 108 && b == 160 && c in 160..175 -> false
                a >= 224 -> false
                else -> true
            }
        }
        if (address is Inet6Address) {
            val bytes = address.address
            val first = bytes[0].toInt() and 0xff
            if (first and 0xe0 != 0x20) return false
            val documentationPrefix = bytes[0] == 0x20.toByte() &&
                bytes[1] == 0x01.toByte() &&
                bytes[2] == 0x0d.toByte() &&
                bytes[3] == 0xb8.toByte()
            return !documentationPrefix
        }
        return false
    }

    private fun parseNumericAddress(value: String): InetAddress? {
        val candidate = value.trim()
        if (candidate.isEmpty()) return null
        if (':' !in candidate) {
            val parts = candidate.split('.')
            if (parts.size != 4) return null
            val bytes = parts.map { part ->
                if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
                part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }
            return InetAddress.getByAddress(bytes.map(Int::toByte).toByteArray())
        }
        if (!candidate.matches(IPV6_LITERAL)) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }

    private fun isValidHostname(hostname: String): Boolean {
        if (hostname.length !in 1..253 || '.' !in hostname) return false
        return hostname.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isAsciiLetterOrDigit() &&
                label.last().isAsciiLetterOrDigit() &&
                label.all { it.isAsciiLetterOrDigit() || it == '-' }
        }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in '0'..'9'

    private val WHITESPACE = Regex("\\s+")
    private val IPV6_LITERAL = Regex("[0-9a-fA-F:.]+")
}
