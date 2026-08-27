package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogParser
import takagi.ru.monica.steam.store.catalog.data.buildSteamStoreCatalogQuery
import takagi.ru.monica.steam.store.data.catalogCacheName
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection

class SteamStoreFilterServiceTest {
    @Test
    fun combinedFiltersProduceStableOfficialSearchParameters() {
        val selection = SteamStoreFilterSelection(
            maxPrice = "50",
            supportedLanguageIds = linkedSetOf("schinese", "english"),
            tagIds = linkedSetOf(492, 19)
        )

        val query = buildSteamStoreCatalogQuery(
            filter = SteamStoreBrowseFilter.SPECIALS,
            filters = selection,
            start = 24,
            count = 24,
            language = "schinese",
            queryText = "Portal"
        )

        assertEquals("Portal", query["term"])
        assertEquals("50", query["maxprice"])
        assertEquals("english,schinese", query["supportedlang"])
        assertEquals("19,492", query["tags"])
        assertEquals("1", query["specials"])
        assertEquals("24", query["start"])
    }

    @Test
    fun freeBrowseFilterCannotBeOverriddenByAdvancedPrice() {
        val query = buildSteamStoreCatalogQuery(
            filter = SteamStoreBrowseFilter.FREE,
            filters = SteamStoreFilterSelection(maxPrice = "100"),
            start = 0,
            count = 24,
            language = "schinese"
        )

        assertEquals("free", query["maxprice"])
    }

    @Test
    fun filteredCatalogCachesAreSeparatedInsideCurrentContentCacheVersion() {
        val defaultName = catalogCacheName(
            accountId = 7L,
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            filters = SteamStoreFilterSelection()
        )
        val filteredName = catalogCacheName(
            accountId = 7L,
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            filters = SteamStoreFilterSelection(tagIds = setOf(19))
        )

        assertEquals("v3_account_7_catalog_top_sellers.json", defaultName)
        assertNotEquals(defaultName, filteredName)
        assertTrue(filteredName.contains("t_19"))
    }

    @Test
    fun catalogRowsKeepSteamTagIdsForCardLabels() {
        val payload = """
            {
              "start": 0,
              "total_count": 1,
              "results_html": "<a data-ds-appid='620' data-ds-tagids='[19,492,1663]' class='search_result_row'><span class='title'>Portal 2</span></a>"
            }
        """.trimIndent()

        val item = SteamStoreCatalogParser.parse(
            payload,
            SteamStoreBrowseFilter.ALL
        ).items.single()

        assertEquals(listOf(19, 492, 1663), item.tagIds)
    }

    @Test
    fun filteredSearchUsesTheCrossRegionCatalogPath() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreService.kt"
        ).readText()
        val searchBlock = service
            .substringAfter("suspend fun search(")
            .substringBefore("fun detail(")

        assertTrue(searchBlock.contains("if (filters.isActive)"))
        assertTrue(searchBlock.contains("catalogService.search("))
        assertTrue(searchBlock.contains("STEAM_STORE_DISCOVERY_COUNTRY_CODES"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
