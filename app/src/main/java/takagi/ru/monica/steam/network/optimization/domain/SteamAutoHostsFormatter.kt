package takagi.ru.monica.steam.network.optimization.domain

object SteamAutoHostsFormatter {
    internal const val BEGIN_MARKER = "# Etoile automatic DNS begin"
    internal const val END_MARKER = "# Etoile automatic DNS end"

    fun merge(
        existingText: String,
        result: SteamDnsOptimizationScanResult,
        scannedAtMillis: Long = System.currentTimeMillis()
    ): String {
        require(result.selectedRoutes.isNotEmpty()) { "No verified Steam routes" }
        val generatedBlock = buildString {
            appendLine(BEGIN_MARKER)
            appendLine("# scanned_at_ms=$scannedAtMillis")
            appendLine("# average_latency_ms=${result.averageLatencyMillis.orEmptyValue()}")
            appendLine("# provider_ids=${result.providerIds.joinToString(",")}")
            appendLine("# selected_hosts=${result.availableHostCount}")
            appendLine("# total_hosts=${result.totalHostCount}")
            appendLine("# missing_hosts=${result.missingHostnames.joinToString(",")}")
            result.selectedRoutes.forEach { route ->
                append(route.address)
                append(' ')
                append(route.hostname)
                append(" # source=")
                append(route.providerIds.joinToString("+"))
                append(" latency_ms=")
                appendLine(route.latencyMillis)
            }
            append(END_MARKER)
        }
        val preservedText = stripGeneratedBlock(existingText).trim()
        return if (preservedText.isEmpty()) {
            generatedBlock
        } else {
            "$generatedBlock\n\n$preservedText"
        }
    }

    fun summary(text: String): SteamAutoHostsSummary? {
        val block = generatedBlockLines(text) ?: return null
        val values = block.mapNotNull { line ->
            val content = line.trim().removePrefix("#").trim()
            val separator = content.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            content.substring(0, separator) to content.substring(separator + 1)
        }.toMap()
        val scannedAtMillis = values["scanned_at_ms"]?.toLongOrNull() ?: return null
        val selectedHostCount = values["selected_hosts"]?.toIntOrNull() ?: return null
        val totalHostCount = values["total_hosts"]?.toIntOrNull() ?: return null
        return SteamAutoHostsSummary(
            scannedAtMillis = scannedAtMillis,
            averageLatencyMillis = values["average_latency_ms"]
                ?.toLongOrNull()
                ?.takeIf { it >= 0L },
            providerIds = values["provider_ids"]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
            selectedHostCount = selectedHostCount,
            totalHostCount = totalHostCount,
            missingHostnames = values["missing_hosts"]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
        )
    }

    fun routes(text: String): List<SteamDnsSelectedRoute> {
        val block = generatedBlockLines(text) ?: return emptyList()
        return block.mapNotNull { line ->
            val content = line.substringBefore('#').trim()
            if (content.isEmpty()) return@mapNotNull null
            val tokens = content.split(WHITESPACE).filter(String::isNotBlank)
            if (tokens.size < 2) return@mapNotNull null
            val comment = line.substringAfter('#', "")
            val latencyMillis = LATENCY_PATTERN.find(comment)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?: return@mapNotNull null
            val providerIds = SOURCE_PATTERN.find(comment)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .split('+')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            SteamDnsSelectedRoute(
                hostname = SteamHostsRuleParser.normalizeHostname(tokens[1]),
                address = tokens[0],
                providerIds = providerIds,
                latencyMillis = latencyMillis
            )
        }.distinctBy { route -> route.hostname to route.address }
    }

    fun stripGeneratedBlock(text: String): String {
        var insideGeneratedBlock = false
        return text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .filter { line ->
                when (line.trim()) {
                    BEGIN_MARKER -> {
                        insideGeneratedBlock = true
                        false
                    }
                    END_MARKER -> {
                        insideGeneratedBlock = false
                        false
                    }
                    else -> !insideGeneratedBlock
                }
            }
            .joinToString("\n")
            .trim()
    }

    private fun generatedBlockLines(text: String): List<String>? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val start = lines.indexOfFirst { it.trim() == BEGIN_MARKER }
        if (start < 0) return null
        val end = lines.indexOfFirst { index, line ->
            index > start && line.trim() == END_MARKER
        }
        if (end <= start) return null
        return lines.subList(start + 1, end)
    }

    private fun List<String>.indexOfFirst(predicate: (Int, String) -> Boolean): Int {
        forEachIndexed { index, value ->
            if (predicate(index, value)) return index
        }
        return -1
    }

    private fun Long?.orEmptyValue(): Long = this ?: -1L

    private val WHITESPACE = Regex("\\s+")
    private val SOURCE_PATTERN = Regex("(?:^|\\s)source=([^\\s]+)")
    private val LATENCY_PATTERN = Regex("(?:^|\\s)latency_ms=(\\d+)")
}
