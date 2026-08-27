package takagi.ru.monica.steam.web.domain

import java.net.URI

/**
 * Keeps Steam Family View's unlock cookie in process memory only.
 * The PIN is handled exclusively by Steam's own page and is never exposed here.
 */
internal class SteamFamilyViewSession(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private data class Entry(
        val value: String,
        val expiresAtMillis: Long,
    )

    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()

    init {
        require(ttlMillis > 0L)
    }

    fun capture(
        accountSteamId: String,
        cookieHeader: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val accountId = normalizeAccountSteamId(accountSteamId) ?: return false
        val value = cookieHeader
            ?.split(';')
            ?.asSequence()
            ?.map(String::trim)
            ?.mapNotNull(::parseCookiePart)
            ?.firstOrNull { (name, _) -> name.equals(COOKIE_NAME, ignoreCase = true) }
            ?.second
            ?.takeIf(::isSafeCookieValue)
            ?: return false
        val expiresAt = if (Long.MAX_VALUE - nowMillis < ttlMillis) {
            Long.MAX_VALUE
        } else {
            nowMillis + ttlMillis
        }
        synchronized(lock) {
            entries[accountId] = Entry(value = value, expiresAtMillis = expiresAt)
        }
        return true
    }

    fun cookieFor(
        accountSteamId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String? {
        val accountId = normalizeAccountSteamId(accountSteamId) ?: return null
        return synchronized(lock) {
            val entry = entries[accountId] ?: return@synchronized null
            if (nowMillis >= entry.expiresAtMillis) {
                entries.remove(accountId)
                null
            } else {
                "$COOKIE_NAME=${entry.value}"
            }
        }
    }

    fun clear(accountSteamId: String) {
        val accountId = normalizeAccountSteamId(accountSteamId) ?: return
        synchronized(lock) { entries.remove(accountId) }
    }

    private fun parseCookiePart(part: String): Pair<String, String>? {
        val separator = part.indexOf('=')
        if (separator <= 0 || separator == part.lastIndex) return null
        val name = part.substring(0, separator).trim()
        val value = part.substring(separator + 1).trim()
        return name to value
    }

    private fun normalizeAccountSteamId(value: String): String? = value
        .trim()
        .takeIf { it.length in 1..MAX_ACCOUNT_ID_LENGTH }
        ?.takeIf { id -> id.all(Char::isDigit) }

    private fun isSafeCookieValue(value: String): Boolean =
        value.length in 1..MAX_COOKIE_VALUE_LENGTH && value.all { character ->
            character.code in 0x21..0x7E && character != ';' && character != ','
        }

    private companion object {
        const val COOKIE_NAME = "steamparental"
        const val MAX_ACCOUNT_ID_LENGTH = 32
        const val MAX_COOKIE_VALUE_LENGTH = 4_096
        const val DEFAULT_TTL_MILLIS = 12L * 60L * 60L * 1_000L
    }
}

internal object SteamFamilyViewSessions {
    private val session = SteamFamilyViewSession()

    fun capture(accountSteamId: String, cookieHeader: String?): Boolean =
        session.capture(accountSteamId, cookieHeader)

    fun cookieFor(accountSteamId: String): String? = session.cookieFor(accountSteamId)

    fun clear(accountSteamId: String) = session.clear(accountSteamId)
}

internal object SteamFamilyViewCookieSourcePolicy {
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase().orEmpty()
        host == "steampowered.com" ||
            host.endsWith(".steampowered.com") ||
            host == "steamcommunity.com" ||
            host.endsWith(".steamcommunity.com")
    }.getOrDefault(false)
}
