package takagi.ru.monica.steam.store.domain

import java.net.URI
import java.util.Locale

internal fun normalizeSteamStoreWebsiteUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    val candidate = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        runCatching { URI(trimmed).scheme }.getOrNull().isNullOrBlank() -> "https://$trimmed"
        else -> trimmed
    }
    return runCatching {
        val uri = URI(candidate)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        candidate.takeIf {
            scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }
    }.getOrNull()
}
