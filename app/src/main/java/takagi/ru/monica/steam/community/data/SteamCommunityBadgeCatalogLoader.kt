package takagi.ru.monica.steam.community.data

import takagi.ru.monica.steam.community.domain.SteamCommunityBadge

internal object SteamCommunityBadgeCatalogLoader {
    fun load(
        steamId: String,
        maxPages: Int = MAX_BADGE_PAGES,
        fetchPage: (Int) -> String
    ): List<SteamCommunityBadge> {
        require(maxPages > 0) { "maxPages must be positive" }
        val firstPage = fetchPage(1)
        if (firstPage.isBlank()) return emptyList()
        val pageCount = SteamCommunityParser.badgePageCount(firstPage).coerceAtMost(maxPages)
        val rows = buildList {
            addAll(SteamCommunityParser.badgeDetails(firstPage, steamId))
            for (page in 2..pageCount) {
                val html = runCatching { fetchPage(page) }.getOrNull().orEmpty()
                if (html.isNotBlank()) {
                    addAll(SteamCommunityParser.badgeDetails(html, steamId))
                }
            }
        }
        return rows.distinctBy { badge ->
            Triple(badge.appId, badge.badgeId, badge.borderColor)
        }
    }

    private const val MAX_BADGE_PAGES = 100
}
