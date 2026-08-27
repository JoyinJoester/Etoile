package takagi.ru.monica.steam.profile.viewer.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievementProgress
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileSummary
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGroup
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget

internal object SteamProfileViewerParser {
    fun parseProfileSummary(
        payload: JsonObject,
        target: SteamProfileViewerTarget
    ): SteamProfileSummary? {
        val root = payload.obj("response") ?: payload
        val player = root.array("players")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("steamid") == target.steamId }
            ?: return null
        return SteamProfileSummary(
            steamId = target.steamId,
            personaName = player.string("personaname").ifBlank { target.fallbackName },
            realName = player.string("realname"),
            avatarUrl = player.string("avatarfull")
                .ifBlank { player.string("avatarmedium") }
                .ifBlank { target.fallbackAvatarUrl },
            profileUrl = player.string("profileurl").ifBlank { target.fallbackProfileUrl },
            personaState = SteamPersonaState.fromCode(player.int("personastate")),
            lastLogoff = player.long("lastlogoff"),
            timeCreated = player.long("timecreated"),
            currentGameId = player.string("gameid"),
            currentGameName = player.string("gameextrainfo"),
            countryCode = player.string("loccountrycode"),
            communityVisibilityState = player.int("communityvisibilitystate")
        )
    }

    fun parseSteamLevel(response: ByteArray): Int? = SteamProtoReader(response)
        .parse()[1]
        ?.asLong
        ?.toInt()
        ?.takeIf { it >= 0 }

    fun parseOwnedGames(response: ByteArray): List<SteamGame> =
        SteamGameLibraryService.parseOwnedGames(response).map { game ->
            game.copy(
                headerImageUrl =
                    "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" +
                        "${game.appId}/header.jpg"
            )
        }

    fun parseAchievementProgress(response: ByteArray): Map<Int, SteamGameAchievementProgress> =
        SteamGameLibraryService.parseAchievementProgress(response)

    fun hasAchievementDefinitions(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        return SteamProtoReader(response).parseAll().any { field ->
            field.number == 1 && field.bytes != null
        }
    }

    fun applyAchievementProgress(
        games: List<SteamGame>,
        progress: Map<Int, SteamGameAchievementProgress>
    ): List<SteamGame> = games.map { game ->
        val item = progress[game.appId] ?: return@map game
        game.copy(
            achievementUnlockedCount = item.unlocked,
            achievementTotalCount = item.total,
            allAchievementsUnlocked = item.allUnlocked
        )
    }

    fun parseCommunityCounts(html: String): SteamProfileCommunityCounts? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html)
        val counts = SteamProfileCommunityCounts(
            friendCount = document.selectFirst(
                ".profile_friend_links .profile_count_link_total"
            )?.text().toProfileCount(),
            groupCount = document.selectFirst(
                ".profile_group_links .profile_count_link_total"
            )?.text().toProfileCount(),
            badgeCount = document.selectFirst(
                "a[href*=\"/badges/\"] .profile_count_link_total"
            )?.text().toProfileCount()
        )
        return counts.takeIf {
            it.friendCount != null || it.groupCount != null || it.badgeCount != null
        }
    }

    fun parseCommunityFriends(html: String): List<SteamFriend> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, "https://steamcommunity.com/")
        return document.select(".friend_block_v2[data-steamid]").mapNotNull { row ->
            val steamId = row.attr("data-steamid").takeIf {
                it.matches(Regex("7656119\\d{10}"))
            } ?: return@mapNotNull null
            val profileUrl = row.selectFirst("a.selectable_overlay[href]")
                ?.absUrl("href").orEmpty()
            val personaName = row.attr("data-search")
                .substringBefore(';')
                .trim()
                .ifBlank {
                    row.selectFirst(".friend_block_content")?.ownText().orEmpty().trim()
                }
            val statusText = row.selectFirst(".friend_small_text")?.text().orEmpty().trim()
            val isOnline = row.hasClass("online") || row.hasClass("in-game") ||
                row.hasClass("in_game")
            SteamFriend(
                steamId = steamId,
                relationship = SteamFriendRelationship.FRIEND,
                personaName = personaName,
                avatarUrl = row.selectFirst(".player_avatar img[src]")?.absUrl("src").orEmpty(),
                profileUrl = profileUrl.ifBlank {
                    "https://steamcommunity.com/profiles/$steamId/"
                },
                personaState = if (isOnline) SteamPersonaState.ONLINE else SteamPersonaState.OFFLINE,
                gameName = statusText.takeIf { row.hasClass("in-game") || row.hasClass("in_game") }
                    .orEmpty()
            )
        }.distinctBy(SteamFriend::steamId)
    }

    fun parseCommunityGroups(html: String): List<SteamProfileGroup> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, "https://steamcommunity.com/")
        return document.select(".group_block.invite_row").mapNotNull { row ->
            val titleLink = row.selectFirst(".groupTitle a.linkTitle[href]")
                ?: return@mapNotNull null
            val profileUrl = titleLink.absUrl("href")
            val name = titleLink.text().trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val chatHref = row.selectFirst("a[href*=OpenGroupChat]")?.attr("href").orEmpty()
            val groupId = GROUP_CHAT_ID.find(chatHref)?.groupValues?.getOrNull(1)
                ?.takeIf(String::isNotBlank)
                ?: profileUrl.substringAfterLast('/').takeIf(String::isNotBlank)
                ?: name
            SteamProfileGroup(
                groupId = groupId,
                name = name,
                avatarUrl = row.selectFirst(".group_block_medium img[src]")?.absUrl("src").orEmpty(),
                profileUrl = profileUrl,
                memberCount = row.selectFirst("a[href$=\"/members\"]")?.text().toProfileCount(),
                onlineCount = row.selectFirst(".membersOnline")?.text().toProfileCount(),
                inGameCount = row.selectFirst(".membersInGame")?.text().toProfileCount(),
                groupChatCount = row.selectFirst("a[href*=OpenGroupChat]")?.text().toProfileCount()
            )
        }.distinctBy(SteamProfileGroup::groupId)
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int {
        val value = this[key] as? JsonPrimitive ?: return 0
        return value.intOrNull ?: value.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun JsonObject.long(key: String): Long {
        val value = this[key] as? JsonPrimitive ?: return 0L
        return value.longOrNull ?: value.contentOrNull?.toLongOrNull() ?: 0L
    }

    private fun String?.toProfileCount(): Int? = this
        ?.let { value -> value.filter { character -> character.isDigit() } }
        ?.takeIf(String::isNotBlank)
        ?.toIntOrNull()

    private val GROUP_CHAT_ID = Regex("OpenGroupChat\\(\\s*['\"](\\d+)['\"]")
}

internal data class SteamProfileCommunityCounts(
    val friendCount: Int?,
    val groupCount: Int?,
    val badgeCount: Int?
)
