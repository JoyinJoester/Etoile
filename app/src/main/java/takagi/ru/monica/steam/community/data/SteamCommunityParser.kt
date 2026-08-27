package takagi.ru.monica.steam.community.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.community.domain.SteamCommunityRecentGame

internal object SteamCommunityParser {
    fun profile(payload: JsonObject): SteamCommunityProfile? {
        val raw = (payload.obj("response") ?: payload).array("players")
            .firstOrNull() as? JsonObject ?: return null
        val steamId = raw.string("steamid")
        if (!steamId.matches(STEAM_ID_PATTERN)) return null
        return SteamCommunityProfile(
            steamId = steamId,
            displayName = raw.string("personaname").ifBlank { steamId },
            realName = raw.string("realname"),
            avatarUrl = raw.string("avatarfull").ifBlank { raw.string("avatarmedium") },
            profileUrl = raw.string("profileurl"),
            countryCode = raw.string("loccountrycode"),
            stateCode = raw.string("locstatecode"),
            cityId = raw.int("loccityid"),
            summary = raw.string("summary"),
            createdAt = raw.long("timecreated"),
            lastLogoff = raw.long("lastlogoff"),
            visibilityState = raw.int("communityvisibilitystate")
        )
    }

    fun level(payload: JsonObject): Int? =
        (payload.obj("response") ?: payload).intOrNull("player_level")

    fun badges(payload: JsonObject): ParsedBadges {
        val root = payload.obj("response") ?: payload
        return ParsedBadges(
            badges = root.array("badges").mapNotNull { element ->
                val raw = element as? JsonObject ?: return@mapNotNull null
                val id = raw.int("badgeid").takeIf { it > 0 } ?: return@mapNotNull null
                SteamCommunityBadge(
                    badgeId = id,
                    level = raw.int("level"),
                    xp = raw.int("xp"),
                    completionTime = raw.long("completion_time"),
                    scarcity = raw.int("scarcity"),
                    appId = raw.int("appid"),
                    borderColor = raw.int("border_color")
                )
            },
            playerXp = root.intOrNull("player_xp"),
            playerXpNeededToLevelUp = root.intOrNull("player_xp_needed_to_level_up")
        )
    }

    fun badgeDetails(html: String, steamId: String): List<SteamCommunityBadge> {
        if (html.isBlank()) return emptyList()
        val baseUrl = "https://steamcommunity.com/profiles/$steamId/"
        return Jsoup.parse(html, baseUrl)
            .select(".badge_row")
            .mapNotNull(::badgeDetail)
            .distinctBy(::badgeMergeKey)
    }

    fun badgePageCount(html: String): Int {
        if (html.isBlank()) return 1
        return Jsoup.parse(html)
            .select(".pagelink[href], .pagebtn[href]")
            .mapNotNull { link ->
                BADGE_PAGE_NUMBER.find(link.attr("href"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1
    }

    fun mergeBadgeDetails(
        badges: List<SteamCommunityBadge>,
        details: List<SteamCommunityBadge>
    ): List<SteamCommunityBadge> {
        val detailsByKey = details.associateBy(::badgeMergeKey)
        val merged = badges.map { badge ->
            val detail = detailsByKey[badgeMergeKey(badge)] ?: return@map badge
            badge.copy(
                level = badge.level.takeIf { it > 0 } ?: detail.level,
                xp = badge.xp.takeIf { it > 0 } ?: detail.xp,
                name = detail.name.ifBlank { badge.name },
                gameName = detail.gameName.ifBlank { badge.gameName },
                iconUrl = detail.iconUrl.ifBlank { badge.iconUrl },
                detailUrl = detail.detailUrl.ifBlank { badge.detailUrl },
                unlockedAt = detail.unlockedAt.ifBlank { badge.unlockedAt },
                isUnlocked = badge.isUnlocked || detail.isUnlocked
            )
        }
        val knownKeys = badges.mapTo(hashSetOf(), ::badgeMergeKey)
        return merged + details.filterNot { badgeMergeKey(it) in knownKeys }
    }

    fun recentGames(payload: JsonObject): List<SteamCommunityRecentGame> =
        (payload.obj("response") ?: payload).array("games").mapNotNull { element ->
            val raw = element as? JsonObject ?: return@mapNotNull null
            val appId = raw.int("appid").takeIf { it > 0 } ?: return@mapNotNull null
            SteamCommunityRecentGame(
                appId = appId,
                name = raw.string("name").ifBlank { "App $appId" },
                iconUrl = raw.string("img_icon_url").toCommunityIconUrl(appId),
                playtimeForeverMinutes = raw.int("playtime_forever"),
                playtimeTwoWeeksMinutes = raw.int("playtime_2weeks"),
                lastPlayedAt = raw.long("rtime_last_played")
            )
        }

    data class ParsedBadges(
        val badges: List<SteamCommunityBadge>,
        val playerXp: Int?,
        val playerXpNeededToLevelUp: Int?
    )

    private fun String.toCommunityIconUrl(appId: Int): String = takeIf(String::isNotBlank)
        ?.let { "https://media.steampowered.com/steamcommunity/public/images/apps/$appId/$it.jpg" }
        .orEmpty()

    private fun badgeDetail(row: Element): SteamCommunityBadge? {
        val key = parseBadgeRowKey(row.id()) ?: parseBadgeHrefKey(
            row.selectFirst(".badge_row_overlay")?.absUrl("href").orEmpty()
        ) ?: return null
        val infoTitle = row.selectFirst(".badge_info_title")?.text().orEmpty().trim()
        val pageTitle = row.selectFirst(".badge_title")?.clone()?.also {
            it.select(".badge_view_details, .badge_title_stats").remove()
        }?.text().orEmpty().trim()
        val icon = row.selectFirst("img.badge_icon, .badge_info_image img, img[data-delayed-image]")
            ?.let { image ->
                image.absUrl("data-delayed-image").ifBlank { image.absUrl("src") }
        }.orEmpty().takeIf(::isSafeImageUrl).orEmpty()
        val detailUrl = row.selectFirst(".badge_row_overlay")?.absUrl("href")
            .orEmpty()
            .takeIf(::isSafeCommunityUrl)
            .orEmpty()
        val infoText = row.selectFirst(".badge_info_description")?.text().orEmpty()
        val level = BADGE_LEVEL.find(infoText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val unlockedAt = row.selectFirst(".badge_info_unlocked")?.text().orEmpty().trim()
        return SteamCommunityBadge(
            badgeId = key.badgeId,
            level = level,
            xp = BADGE_XP.find(infoText)?.groupValues?.getOrNull(1)
                ?.replace(",", "")
                ?.replace(" ", "")
                ?.toIntOrNull()
                ?: 0,
            completionTime = 0L,
            scarcity = 0,
            appId = key.appId,
            borderColor = key.borderColor,
            name = infoTitle.ifBlank { pageTitle },
            gameName = pageTitle.takeIf { key.appId > 0 }.orEmpty(),
            iconUrl = icon,
            detailUrl = detailUrl,
            unlockedAt = unlockedAt,
            isUnlocked = unlockedAt.isNotBlank() || level > 0
        )
    }

    private fun parseBadgeRowKey(value: String): BadgeKey? {
        GENERIC_BADGE_ID.matchEntire(value)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return BadgeKey(appId = 0, badgeId = it, borderColor = 0)
        }
        val game = GAME_BADGE_ID.matchEntire(value) ?: return null
        return BadgeKey(
            appId = game.groupValues[1].toIntOrNull() ?: return null,
            badgeId = game.groupValues[2].toIntOrNull() ?: return null,
            borderColor = game.groupValues[3].toIntOrNull() ?: 0
        )
    }

    private fun parseBadgeHrefKey(value: String): BadgeKey? {
        val generic = GENERIC_BADGE_HREF.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (generic != null) return BadgeKey(appId = 0, badgeId = generic, borderColor = 0)
        val appId = GAME_BADGE_HREF.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        return BadgeKey(appId = appId, badgeId = 1, borderColor = 0)
    }

    private fun badgeMergeKey(badge: SteamCommunityBadge): BadgeKey = BadgeKey(
        appId = badge.appId,
        badgeId = badge.badgeId,
        borderColor = badge.borderColor
    )

    private fun isSafeImageUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) &&
            !value.contains("trans.gif", ignoreCase = true)

    private fun isSafeCommunityUrl(value: String): Boolean =
        value.startsWith("https://steamcommunity.com/", ignoreCase = true)

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        ?: string(key).toIntOrNull() ?: 0
    private fun JsonObject.intOrNull(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        ?: string(key).toIntOrNull()
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
        ?: string(key).toLongOrNull() ?: 0L

    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
    private val GENERIC_BADGE_ID = Regex("badge_badge_(\\d+)")
    private val GAME_BADGE_ID = Regex("badge_gamebadge_(\\d+)_(\\d+)_(\\d+)")
    private val GENERIC_BADGE_HREF = Regex("/badges/(\\d+)/?", RegexOption.IGNORE_CASE)
    private val GAME_BADGE_HREF = Regex("/gamecards/(\\d+)/?", RegexOption.IGNORE_CASE)
    private val BADGE_LEVEL = Regex("(?i)\\blevel\\s+([0-9]+)")
    private val BADGE_XP = Regex("(?i)([0-9][0-9, ]*)\\s*(?:XP|经验值?)")
    private val BADGE_PAGE_NUMBER = Regex("[?&]p=(\\d+)", RegexOption.IGNORE_CASE)

    private data class BadgeKey(val appId: Int, val badgeId: Int, val borderColor: Int)
}
