package takagi.ru.monica.steam.friends.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

internal sealed interface SteamFriendLookup {
    data class SteamId(val value: String) : SteamFriendLookup
    data class VanityName(val value: String) : SteamFriendLookup
    data class QuickInvite(val url: String) : SteamFriendLookup
    data class PersonaName(val value: String) : SteamFriendLookup
}

internal data class SteamFriendSearchHit(
    val steamId: String,
    val personaName: String,
    val avatarUrl: String,
    val profileUrl: String
)

internal object SteamFriendDiscoveryParser {
    private const val STEAM_ID64_ACCOUNT_OFFSET = 76561197960265728L
    private const val MAX_ACCOUNT_ID = 0xffff_ffffL
    private val steamId64Pattern = Regex("7656119\\d{10}")

    fun classify(rawQuery: String): SteamFriendLookup {
        val query = rawQuery.trim()
        val urlCandidate = when {
            query.startsWith("steamcommunity.com/", ignoreCase = true) -> "https://$query"
            query.startsWith("www.steamcommunity.com/", ignoreCase = true) -> "https://$query"
            query.startsWith("s.team/", ignoreCase = true) -> "https://$query"
            else -> query
        }
        val url = urlCandidate.toHttpUrlOrNull()
        if (url != null) {
            val host = url.host.lowercase()
            val segments = url.pathSegments.filter(String::isNotBlank)
            if (host == "s.team" && segments.firstOrNull().equals("p", ignoreCase = true)) {
                return SteamFriendLookup.QuickInvite(url.toString())
            }
            if (host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")) {
                when (segments.firstOrNull()?.lowercase()) {
                    "profiles" -> segments.getOrNull(1)
                        ?.takeIf(::isSteamId64)
                        ?.let { return SteamFriendLookup.SteamId(it) }
                    "id" -> segments.getOrNull(1)
                        ?.takeIf(::isSafeVanityName)
                        ?.let { return SteamFriendLookup.VanityName(it) }
                    "user" -> return SteamFriendLookup.QuickInvite(url.toString())
                }
            }
        }

        if (isSteamId64(query)) return SteamFriendLookup.SteamId(query)
        query.filterNot(Char::isWhitespace).toLongOrNull()?.let { accountId ->
            if (accountId in 1..MAX_ACCOUNT_ID) {
                return SteamFriendLookup.SteamId(
                    (STEAM_ID64_ACCOUNT_OFFSET + accountId).toString()
                )
            }
        }
        return SteamFriendLookup.PersonaName(query)
    }

    fun parseSearchHtml(html: String): List<SteamFriendSearchHit> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parseBodyFragment(html, "https://steamcommunity.com/")
        return document.select(".search_row").mapNotNull { row ->
            val accountId = row.selectFirst("[data-miniprofile]")
                ?.attr("data-miniprofile")
                ?.toLongOrNull()
                ?.takeIf { it in 1..MAX_ACCOUNT_ID }
                ?: return@mapNotNull null
            val profile = row.selectFirst("a.searchPersonaName[href]")
                ?: return@mapNotNull null
            SteamFriendSearchHit(
                steamId = (STEAM_ID64_ACCOUNT_OFFSET + accountId).toString(),
                personaName = profile.text().trim(),
                avatarUrl = row.selectFirst(".avatarMedium img[src]")
                    ?.absUrl("src")
                    .orEmpty(),
                profileUrl = profile.absUrl("href")
            )
        }.distinctBy(SteamFriendSearchHit::steamId)
    }

    fun parseProfileSteamId(
        payload: String,
        baseUrl: String = "https://steamcommunity.com/",
        excludedSteamId: String? = null
    ): String? {
        if (payload.isBlank()) return null
        val xmlDocument = Jsoup.parse(payload, baseUrl, Parser.xmlParser())
        xmlDocument.selectFirst("steamID64")
            ?.text()
            ?.trim()
            ?.takeIf { isSteamId64(it) && it != excludedSteamId }
            ?.let { return it }

        val document = Jsoup.parse(payload, baseUrl)
        sequenceOf(
            document.selectFirst("link[rel=canonical][href]")?.absUrl("href"),
            document.selectFirst("meta[property=og:url][content]")?.absUrl("content")
        ).filterNotNull().forEach { url ->
            profileSteamId(url)?.takeIf { it != excludedSteamId }?.let { return it }
        }

        document.select("[data-miniprofile]").forEach { element ->
            val accountId = element.attr("data-miniprofile").toLongOrNull()
                ?.takeIf { it in 1..MAX_ACCOUNT_ID }
                ?: return@forEach
            val steamId = (STEAM_ID64_ACCOUNT_OFFSET + accountId).toString()
            if (steamId != excludedSteamId) return steamId
        }

        val candidates = buildList {
            PROFILE_URL_PATTERN.findAll(payload).forEach { add(it.groupValues[1]) }
            STEAM_ID_FIELD_PATTERN.findAll(payload).forEach { add(it.groupValues[1]) }
        }
        return candidates.firstOrNull { isSteamId64(it) && it != excludedSteamId }
    }

    private fun profileSteamId(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val segments = parsed.pathSegments.filter(String::isNotBlank)
        if (!segments.firstOrNull().equals("profiles", ignoreCase = true)) return null
        return segments.getOrNull(1)?.takeIf(::isSteamId64)
    }

    private fun isSteamId64(value: String): Boolean = steamId64Pattern.matches(value)

    private fun isSafeVanityName(value: String): Boolean =
        value.isNotBlank() && value.length <= 64 && value.all {
            it.isLetterOrDigit() || it == '_' || it == '-'
        }

    private val PROFILE_URL_PATTERN = Regex(
        """(?i)https?://(?:www\.)?steamcommunity\.com/profiles/(7656119\d{10})"""
    )
    private val STEAM_ID_FIELD_PATTERN = Regex(
        """["']steamid["']\s*:\s*["'](7656119\d{10})["']""",
        RegexOption.IGNORE_CASE
    )
}
