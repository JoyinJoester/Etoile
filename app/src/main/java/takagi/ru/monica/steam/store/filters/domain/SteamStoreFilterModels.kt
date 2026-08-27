package takagi.ru.monica.steam.store.filters.domain

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreFilterSelection(
    val maxPrice: String? = null,
    val supportedLanguageIds: Set<String> = emptySet(),
    val tagIds: Set<Int> = emptySet()
) {
    val isActive: Boolean
        get() = maxPrice != null || supportedLanguageIds.isNotEmpty() || tagIds.isNotEmpty()

    val activeCount: Int
        get() = (if (maxPrice != null) 1 else 0) +
            supportedLanguageIds.size + tagIds.size

    fun normalized(): SteamStoreFilterSelection = copy(
        maxPrice = maxPrice
            ?.trim()
            ?.lowercase()
            ?.takeIf { it == "free" || it.toIntOrNull()?.let { value -> value > 0 } == true },
        supportedLanguageIds = supportedLanguageIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .toSortedSet(),
        tagIds = tagIds.filterTo(sortedSetOf()) { it > 0 }
    )

    fun cacheKey(): String {
        val value = normalized()
        if (!value.isActive) return "default"
        val price = value.maxPrice ?: "any"
        val languages = value.supportedLanguageIds.joinToString("-").ifBlank { "any" }
        val tags = value.tagIds.joinToString("-").ifBlank { "any" }
        val raw = "p_${price}_l_${languages}_t_${tags}"
        if (raw.length <= MAX_READABLE_CACHE_KEY_LENGTH) return raw
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(24)
        return "filters_$digest"
    }

    fun toQueryParameters(): Map<String, String> = buildMap {
        normalized().let { value ->
            value.maxPrice?.let { put("maxprice", it) }
            value.supportedLanguageIds.takeIf(Set<String>::isNotEmpty)?.let {
                put("supportedlang", it.joinToString(","))
            }
            value.tagIds.takeIf(Set<Int>::isNotEmpty)?.let {
                put("tags", it.joinToString(","))
            }
        }
    }
}

private const val MAX_READABLE_CACHE_KEY_LENGTH = 120

@Serializable
data class SteamStoreFilterOption(
    val value: String,
    val label: String
)

@Serializable
data class SteamStoreTagOption(
    val id: Int,
    val label: String
)

@Serializable
data class SteamStoreFilterMetadata(
    val priceOptions: List<SteamStoreFilterOption> = emptyList(),
    val languages: List<SteamStoreFilterOption> = emptyList(),
    val tags: List<SteamStoreTagOption> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
)

internal fun resolveSteamStoreTagLabels(
    tagIds: List<Int>,
    metadata: SteamStoreFilterMetadata?,
    enabled: Boolean
): List<String> {
    if (!enabled || tagIds.isEmpty() || metadata == null) return emptyList()
    val labels = metadata.tags.associate { tag -> tag.id to tag.label }
    return tagIds.mapNotNull(labels::get).distinct()
}

internal fun SteamStoreFilterMetadata.findTagId(label: String): Int? {
    val normalized = label.trim()
    if (normalized.isBlank()) return null
    return tags.firstOrNull { tag -> tag.label.equals(normalized, ignoreCase = true) }?.id
}
