package takagi.ru.monica.steam.network.optimization.domain

import java.net.InetAddress
import java.net.IDN
import java.net.URI

data class SteamNetworkResolverSettings(
    val useSystemDns: Boolean = true,
    val useBuiltInDoh: Boolean = true,
    val customDnsServers: List<String> = emptyList(),
    val customDohEndpoints: List<String> = emptyList(),
    val customDohBootstrapAddresses: Map<String, List<String>> = emptyMap(),
    val preferredProviderIds: List<String> = emptyList(),
    val dynamicDnsEnabled: Boolean = false,
    val disabledBuiltInProviderIds: Set<String> = emptySet(),
    val disabledCustomProviderIds: Set<String> = emptySet(),
    val preferIpv6: Boolean = false
) {
    val configuredProviders: List<SteamDnsProvider>
        get() = buildList {
            if (useSystemDns) add(SteamDnsProvider.SYSTEM)
            if (useBuiltInDoh) addAll(SteamDnsProvider.DEFAULTS.filterNot { it.isSystem })
            addAll(customDnsServers.map(SteamDnsProvider::customDns))
            addAll(
                customDohEndpoints.map { endpoint ->
                    SteamDnsProvider.customDoh(
                        endpoint = endpoint,
                        bootstrapAddresses = customDohBootstrapAddresses[endpoint].orEmpty()
                    )
                }
            )
        }.distinctBy(SteamDnsProvider::id)

    val activeProviders: List<SteamDnsProvider>
        get() {
            val available = configuredProviders.filterNot { provider ->
                when {
                    provider.id in disabledCustomProviderIds -> true
                    provider.isDoh && provider.id in disabledBuiltInProviderIds &&
                        SteamDnsProvider.DEFAULTS.any { it.id == provider.id } -> true
                    else -> false
                }
            }
            if (preferredProviderIds.isEmpty()) return available

            val byId = available.associateBy(SteamDnsProvider::id)
            return buildList {
                preferredProviderIds.forEach { providerId ->
                    byId[providerId]?.let { provider ->
                        if (none { it.id == provider.id }) add(provider)
                    }
                }
                available.forEach { provider ->
                    if (none { it.id == provider.id }) add(provider)
                }
            }
        }

    val hasResolver: Boolean get() = activeProviders.isNotEmpty()
    val hasPreferredProviders: Boolean get() = preferredProviderIds.isNotEmpty()

    fun isProviderEnabled(provider: SteamDnsProvider): Boolean =
        activeProviders.any { it.id == provider.id }

    companion object {
        const val MAX_CUSTOM_DNS = 8
        const val MAX_CUSTOM_DOH = 8
        const val MAX_DOH_BOOTSTRAP_ADDRESSES = 8
    }
}

object SteamResolverInputValidator {
    fun normalizeDnsServer(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 253 || trimmed.any(Char::isWhitespace)) {
            return null
        }
        if ('/' in trimmed || '?' in trimmed || '#' in trimmed || '@' in trimmed) return null
        val unwrapped = unwrapIpLiteral(trimmed) ?: return null
        if (isIpv4(unwrapped) || isIpv6(unwrapped)) return unwrapped.lowercase()
        if (':' in unwrapped) return null
        val normalized = runCatching {
            IDN.toASCII(SteamHostsRuleParser.normalizeHostname(unwrapped))
        }.getOrNull() ?: return null
        return normalized.takeIf(::isValidResolverHostname)
    }

    fun normalizeDohEndpoint(raw: String): String? {
        val value = raw.trim().takeIf { it.length in 1..512 } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return null
        if (uri.port !in listOf(-1, 443)) return null
        return uri.normalize().toASCIIString()
    }

    /**
     * Optional literal IPs used to bootstrap a custom DoH hostname.
     *
     * Supplying these addresses lets OkHttp connect to the DoH server without first resolving
     * that resolver hostname through Android/system DNS. HTTPS still uses the hostname from the
     * DoH URL for SNI and certificate verification.
     */
    fun normalizeBootstrapAddresses(raw: String): List<String>? {
        val value = raw.trim()
        if (value.isEmpty()) return emptyList()
        val tokens = value
            .split(BOOTSTRAP_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (tokens.isEmpty() || tokens.size > SteamNetworkResolverSettings.MAX_DOH_BOOTSTRAP_ADDRESSES) {
            return null
        }

        val normalized = mutableListOf<String>()
        for (token in tokens) {
            val address = normalizeIpLiteral(token) ?: return null
            if (address !in normalized) normalized += address
        }
        return normalized
    }

    fun normalizeIpLiteral(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val unwrapped = unwrapIpLiteral(trimmed) ?: return null
        return when {
            isIpv4(unwrapped) -> unwrapped
            isIpv6(unwrapped) -> unwrapped.lowercase()
            else -> null
        }
    }

    private fun unwrapIpLiteral(value: String): String? {
        val startsWithBracket = value.startsWith('[')
        val endsWithBracket = value.endsWith(']')
        if (startsWithBracket != endsWithBracket) return null
        val unwrapped = if (startsWithBracket) value.substring(1, value.lastIndex) else value
        return unwrapped.takeIf { it.isNotEmpty() && '[' !in it && ']' !in it }
    }

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isIpv6(value: String): Boolean {
        if (value.count { it == ':' } < 2 || !value.matches(IPV6_LITERAL)) return false
        return runCatching { InetAddress.getByName(value).hostAddress }.isSuccess
    }

    private fun isValidResolverHostname(hostname: String): Boolean =
        hostname.length in 1..253 && hostname.contains('.') && hostname.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }

    private val IPV6_LITERAL = Regex("[0-9a-fA-F:.]+")
    private val BOOTSTRAP_SEPARATOR = Regex("[,;\\s]+")
}
