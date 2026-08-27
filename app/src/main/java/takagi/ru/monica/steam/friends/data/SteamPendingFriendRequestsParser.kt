package takagi.ru.monica.steam.friends.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object SteamPendingFriendRequestsParser {
    fun parseSteamIds(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, STEAM_COMMUNITY_BASE)
        return document.select(".invite_row")
            .mapNotNull(::steamIdFromRow)
            .distinct()
    }

    private fun steamIdFromRow(row: Element): String? {
        val directSteamId = sequenceOf(
            row.attr("data-steamid"),
            row.selectFirst("[data-steamid]")?.attr("data-steamid").orEmpty()
        ).firstOrNull(::isSteamId64)
        if (directSteamId != null) return directSteamId

        val accountId = sequenceOf(
            row.attr("data-miniprofile"),
            row.attr("data-accountid"),
            row.selectFirst("[data-miniprofile]")?.attr("data-miniprofile").orEmpty(),
            row.selectFirst("[data-accountid]")?.attr("data-accountid").orEmpty()
        ).mapNotNull { value -> value.toLongOrNull() }
            .firstOrNull { it in 1L..MAX_ACCOUNT_ID }
        if (accountId != null) return (STEAM_ID64_BASE + accountId).toString()

        return row.select("a[href*=/profiles/]")
            .asSequence()
            .mapNotNull { link ->
                PROFILE_STEAM_ID.find(link.absUrl("href").ifBlank { link.attr("href") })
                    ?.groupValues
                    ?.getOrNull(1)
            }
            .firstOrNull(::isSteamId64)
    }

    private fun isSteamId64(value: String): Boolean = STEAM_ID64.matches(value)

    private const val STEAM_COMMUNITY_BASE = "https://steamcommunity.com/"
    private const val STEAM_ID64_BASE = 76561197960265728L
    private const val MAX_ACCOUNT_ID = 0xffff_ffffL
    private val STEAM_ID64 = Regex("7656119\\d{10}")
    private val PROFILE_STEAM_ID = Regex("/profiles/(7656119\\d{10})(?:/|$)")
}
