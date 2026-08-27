package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.points.data.buildSteamPointsMediaUrl
import takagi.ru.monica.steam.store.points.data.buildSteamPointsShopQuery
import takagi.ru.monica.steam.store.points.data.parseSteamPointsBalance
import takagi.ru.monica.steam.store.points.data.parseSteamPointsShopPage
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory

class SteamPointsShopProtocolTest {
    @Test
    fun mediaUrlUsesCurrentCommunityAssetsCdnAndKeepsAbsoluteUrls() {
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset.png",
            buildSteamPointsMediaUrl(730, "/asset.png")
        )
        assertEquals(
            "https://example.com/reward.png",
            buildSteamPointsMediaUrl(730, "https://example.com/reward.png")
        )
        assertEquals("", buildSteamPointsMediaUrl(730, null))
    }

    @Test
    fun queryUsesOfficialCategoryLanguageAndPagingFields() {
        val request = buildSteamPointsShopQuery(
            category = SteamPointsShopCategory.STICKERS,
            language = "schinese",
            count = 24,
            cursor = "next"
        )
        val query = SteamProtoReader(request.toByteArray()).parse()[1]?.bytes!!
        val fields = SteamProtoReader(query).parse()

        assertEquals(listOf(10L), SteamProtoReader.decodePackedVarints(fields[3]?.bytes!!))
        assertEquals("schinese", fields[4]?.asString)
        assertEquals(24, fields[5]?.asInt)
        assertEquals("next", fields[6]?.asString)
    }

    @Test
    fun parsesRewardDefinitionAndCursor() {
        val communityData = SteamProtoWriter().apply {
            writeString(2, "Animated sticker")
            writeString(3, "A reward description")
            writeString(4, "asset-small.png")
            writeString(5, "asset-large.png")
            writeString(6, "asset.webm")
            writeString(7, "asset.mp4")
            writeBool(8, true)
            writeString(10, "asset-small.webm")
            writeString(11, "asset-small.mp4")
            writeString(12, "profile-theme")
            writeBool(13, true)
        }
        val definition = SteamProtoWriter().apply {
            writeVarint(1, 730L)
            writeVarint(2, 42L)
            writeVarint(3, 1L)
            writeVarint(4, 10L)
            writeVarint(6, 1_000L)
            writeMessage(13, communityData)
        }
        val queryResponse = SteamProtoWriter().apply {
            writeMessage(1, definition)
            writeVarint(2, 30L)
            writeVarint(3, 1L)
            writeString(4, "cursor-2")
        }
        val batchResponse = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, queryResponse)
        }
        val response = SteamProtoWriter().apply { writeMessage(1, batchResponse) }

        val page = parseSteamPointsShopPage(
            response.toByteArray(),
            SteamPointsShopCategory.STICKERS
        )

        val item = page.items.single()
        assertEquals(42, item.definitionId)
        assertEquals(1_000L, item.pointCost)
        assertEquals("Animated sticker", item.title)
        assertTrue(item.animated)
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset-small.png",
            item.smallImageUrl
        )
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset-large.png",
            item.largeImageUrl
        )
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset.webm",
            item.webmUrl
        )
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset.mp4",
            item.mp4Url
        )
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset-small.webm",
            item.smallWebmUrl
        )
        assertEquals(
            "https://shared.fastly.steamstatic.com/community_assets/images/items/730/asset-small.mp4",
            item.smallMp4Url
        )
        assertEquals("profile-theme", item.profileThemeId)
        assertTrue(item.tiled)
        assertEquals(item.largeImageUrl, item.imageUrl)
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun parsesAuthenticatedPointsBalance() {
        val summary = SteamProtoWriter().apply { writeVarint(1, 12_345L) }
        val response = SteamProtoWriter().apply { writeMessage(1, summary) }
        assertEquals(12_345L, parseSteamPointsBalance(response.toByteArray()))
    }
}
