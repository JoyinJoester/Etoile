package takagi.ru.monica.steam.community.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SteamCommunityBadgeCatalogLoaderTest {
    @Test
    fun loadsEveryAdvertisedPageAndDeduplicatesRows() {
        val requestedPages = mutableListOf<Int>()
        val pages = mapOf(
            1 to page(
                pageLinks = """
                    <a class="pagelink" href="?p=2&amp;l=english">2</a>
                    <a class="pagelink" href="?p=3&amp;l=english">3</a>
                """.trimIndent(),
                rows = unlockedBadge
            ),
            2 to page(rows = lockedBadge),
            3 to page(rows = unlockedBadge)
        )

        val badges = SteamCommunityBadgeCatalogLoader.load(
            steamId = STEAM_ID,
            fetchPage = { page ->
                requestedPages += page
                pages.getValue(page)
            }
        )

        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(2, badges.size)
        assertFalse(badges.first { it.appId == 620 }.isUnlocked)
    }

    private fun page(pageLinks: String = "", rows: String): String = """
        <div class="pageLinks">$pageLinks</div>
        $rows
    """.trimIndent()

    private companion object {
        const val STEAM_ID = "76561198000000001"
        val unlockedBadge = """
            <div id="badge_badge_1" class="badge_row is_link">
              <a class="badge_row_overlay" href="/profiles/$STEAM_ID/badges/1"></a>
              <div class="badge_info_title">Years of Service</div>
              <div class="badge_info_description">Level 4, 200 XP</div>
              <div class="badge_info_unlocked">Unlocked 1 Jan</div>
            </div>
        """.trimIndent()
        val lockedBadge = """
            <div id="badge_gamebadge_620_0_0" class="badge_row is_link">
              <a class="badge_row_overlay" href="/profiles/$STEAM_ID/gamecards/620/"></a>
              <div class="badge_title">Portal 2</div>
              <div class="badge_info_title">Badge not crafted</div>
              <div class="badge_info_description">0 XP</div>
            </div>
        """.trimIndent()
    }
}
