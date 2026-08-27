package takagi.ru.monica.steam.store

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.preserveCachedReviews
import takagi.ru.monica.steam.store.related.data.SteamStoreRelatedContentParser
import takagi.ru.monica.steam.store.related.domain.SteamStoreRelatedApp
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle

class SteamStoreRelatedContentTest {
    @Test
    fun parsesLocalizedDlcNameAndHeaderImageFromStoreBrowse() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(9, 3364840L)
                writeString(6, "Balatro Soundtrack")
                writeMessage(30, SteamProtoWriter().apply {
                    writeString(1, "steam/apps/3364840/\${FILENAME}")
                    writeString(4, "header_schinese.jpg")
                })
            })
        }.toByteArray()

        val item = SteamStoreRelatedContentParser.parse(response).single()

        assertEquals(3364840, item.appId)
        assertEquals("Balatro Soundtrack", item.name)
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/" +
                "steam/apps/3364840/header_schinese.jpg",
            item.headerImageUrl
        )
    }

    @Test
    fun legacyStoreDetailCacheDefaultsToNoRelatedDlcMetadata() {
        val detail = Json { ignoreUnknownKeys = true }.decodeFromString<SteamStoreDetail>(
            """{"appId":2379780,"name":"Balatro","dlcAppIds":[3364840]}"""
        )

        assertTrue(detail.relatedDlc.isEmpty())
        assertEquals(listOf(3364840), detail.dlcAppIds)
    }

    @Test
    fun cachedDlcMetadataSurvivesAPartialDetailRefresh() {
        val cachedItem = SteamStoreRelatedApp(
            appId = 3364840,
            name = "Balatro Soundtrack",
            headerImageUrl = "cached-header.jpg"
        )
        val cached = SteamStoreDetail(
            appId = 2379780,
            name = "Balatro",
            dlcAppIds = listOf(3364840),
            relatedDlc = listOf(cachedItem)
        )
        val refreshed = cached.copy(relatedDlc = emptyList())
            .preserveCachedReviews(cached)

        assertEquals(listOf(cachedItem), refreshed.relatedDlc)
    }

    @Test
    fun cachedBundleMetadataSurvivesAPartialDetailRefresh() {
        val bundle = SteamStoreBundle(bundleId = 233, title = "Left 4 Dead Bundle")
        val cached = SteamStoreDetail(
            appId = 500,
            name = "Left 4 Dead",
            bundles = listOf(bundle)
        )

        val refreshed = cached.copy(bundles = emptyList()).preserveCachedReviews(cached)

        assertEquals(listOf(bundle), refreshed.bundles)
    }
}
