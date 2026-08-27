package takagi.ru.monica.steam.network.optimization.domain

data class SteamDnsProvider(
    val id: String,
    val displayName: String,
    val dohUrl: String? = null,
    val udpServer: String? = null,
    val bootstrapAddresses: List<String> = emptyList()
) {
    val isSystem: Boolean get() = dohUrl == null && udpServer == null
    val isDoh: Boolean get() = dohUrl != null
    val isUdp: Boolean get() = udpServer != null

    companion object {
        val SYSTEM = SteamDnsProvider(
            id = "system",
            displayName = "System DNS"
        )
        val DNSPOD = SteamDnsProvider(
            id = "dnspod",
            displayName = "DNSPod",
            dohUrl = "https://doh.pub/dns-query",
            bootstrapAddresses = listOf("1.12.12.12", "120.53.53.53")
        )
        val ALIDNS = SteamDnsProvider(
            id = "alidns",
            displayName = "AliDNS",
            dohUrl = "https://dns.alidns.com/dns-query",
            bootstrapAddresses = listOf("223.5.5.5", "223.6.6.6")
        )
        val CLOUDFLARE = SteamDnsProvider(
            id = "cloudflare",
            displayName = "Cloudflare",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapAddresses = listOf("1.1.1.1", "1.0.0.1")
        )
        val GOOGLE = SteamDnsProvider(
            id = "google",
            displayName = "Google",
            dohUrl = "https://dns.google/dns-query",
            bootstrapAddresses = listOf("8.8.8.8", "8.8.4.4")
        )
        val QUAD9_ECS = SteamDnsProvider(
            id = "quad9_ecs",
            displayName = "Quad9 ECS",
            dohUrl = "https://dns11.quad9.net/dns-query",
            bootstrapAddresses = listOf("9.9.9.11", "149.112.112.11")
        )

        val DEFAULTS: List<SteamDnsProvider> = listOf(
            SYSTEM,
            DNSPOD,
            ALIDNS,
            CLOUDFLARE,
            GOOGLE,
            QUAD9_ECS
        )

        fun displayNameFor(id: String): String =
            DEFAULTS.firstOrNull { it.id == id }?.displayName ?: when {
                id.startsWith(CUSTOM_DNS_PREFIX) ->
                    "DNS ${id.substringAfter(CUSTOM_DNS_PREFIX)}"
                id.startsWith(CUSTOM_DOH_PREFIX) ->
                    "DoH ${id.substringAfter(CUSTOM_DOH_PREFIX).substringBefore('|')}"
                else -> id
            }

        fun customDns(server: String): SteamDnsProvider = SteamDnsProvider(
            id = "$CUSTOM_DNS_PREFIX$server",
            displayName = "DNS $server",
            udpServer = server
        )

        fun customDoh(
            endpoint: String,
            bootstrapAddresses: List<String> = emptyList()
        ): SteamDnsProvider {
            val host = runCatching { java.net.URI(endpoint).host }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: "Custom"
            return SteamDnsProvider(
                id = "$CUSTOM_DOH_PREFIX$host|${Integer.toHexString(endpoint.hashCode())}",
                displayName = "DoH $host",
                dohUrl = endpoint,
                bootstrapAddresses = bootstrapAddresses.distinct()
            )
        }

        private const val CUSTOM_DNS_PREFIX = "custom_dns|"
        private const val CUSTOM_DOH_PREFIX = "custom_doh|"
    }
}

data class SteamDnsResolutionResult(
    val provider: SteamDnsProvider,
    val hostname: String,
    val addresses: List<String> = emptyList(),
    val latencyMillis: Long? = null,
    val errorType: String? = null
) {
    val isAvailable: Boolean get() = addresses.isNotEmpty()
}

data class SteamDnsSelectedRoute(
    val hostname: String,
    val address: String,
    val providerIds: List<String>,
    val latencyMillis: Long,
    val httpStatusCode: Int? = null
)

data class SteamDnsOptimizationScanResult(
    val targetHostnames: List<String>,
    val resolutions: List<SteamDnsResolutionResult>,
    val probeResults: List<SteamHostProbeResult>,
    val selectedRoutes: List<SteamDnsSelectedRoute>
) {
    val totalHostCount: Int get() = targetHostnames.size
    val availableHostCount: Int get() = selectedRoutes.map { it.hostname }.distinct().size
    val isComplete: Boolean
        get() = targetHostnames.isNotEmpty() && availableHostCount == totalHostCount
    val isApplicable: Boolean
        get() = targetHostnames.isNotEmpty() && selectedRoutes.isNotEmpty()
    val missingHostnames: List<String>
        get() {
            val covered = selectedRoutes.map { it.hostname.lowercase() }.toSet()
            return targetHostnames.filterNot { it.lowercase() in covered }
        }
    val averageLatencyMillis: Long?
        get() = selectedRoutes.map { it.latencyMillis }.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val providerIds: List<String>
        get() = selectedRoutes.flatMap { it.providerIds }.distinct()
    val providerNames: List<String>
        get() = providerIds.map(SteamDnsProvider::displayNameFor)
}

enum class SteamDnsScanStage {
    RESOLVING,
    VERIFYING,
    RECOVERING
}

data class SteamDnsScanProgress(
    val stage: SteamDnsScanStage,
    val completed: Int,
    val total: Int,
    val currentSource: String? = null
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

data class SteamAutoHostsSummary(
    val scannedAtMillis: Long,
    val averageLatencyMillis: Long?,
    val providerIds: List<String>,
    val selectedHostCount: Int,
    val totalHostCount: Int,
    val missingHostnames: List<String> = emptyList()
) {
    val providerNames: List<String>
        get() = providerIds.map(SteamDnsProvider::displayNameFor)
}
