package takagi.ru.monica.steam.store.freebie.data

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimMethod
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieProductType

class SteamFreebieParserTest {
    @Test
    fun searchParserKeepsOnlyLimitedFreePromotions() {
        val html = """
                <a class="search_result_row" data-ds-appid="606150"
                   href="https://store.steampowered.com/app/606150/Moonlighter/?snr=x">
                  <div class="search_capsule"><img src="https://cdn.example/moonlighter.jpg"></div>
                  <span class="title">Moonlighter</span>
                  <div class="search_price_discount_combined" data-price-final="0">
                    <div class="discount_block" data-discount="100" data-price-final="0">
                      <div class="discount_pct">-100%</div>
                      <div class="discount_original_price">¥70.00</div>
                      <div class="discount_final_price">¥0.00</div>
                    </div>
                  </div>
                </a>
                <a class="search_result_row" data-ds-appid="10"
                   href="https://store.steampowered.com/app/10/Always_Free/">
                  <span class="title">Always Free</span>
                  <div class="search_price_discount_combined" data-price-final="0"></div>
                </a>
        """.trimIndent()
        val payload = buildJsonObject {
            put("success", 1)
            put("results_html", html)
        }.toString()

        val candidates = SteamFreebieSearchParser.parse(payload)

        assertEquals(1, candidates.size)
        assertEquals(606150, candidates.single().appId)
        assertEquals("Moonlighter", candidates.single().name)
        assertEquals("¥70.00", candidates.single().originalPriceText)
        assertEquals("https://store.steampowered.com/app/606150/Moonlighter/", candidates.single().storeUrl)
    }

    @Test
    fun offerPageFindsPermanentFreeLicenseAndExpiry() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 8, 6, 12, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val item = SteamFreebieOfferPageParser.parse(
            candidate = candidate(discountPercent = 100),
            html = """
                <html><head><meta property="og:image" content="https://cdn.example/header.jpg"></head>
                <body><div id="game_area_purchase">
                  <div class="game_area_purchase_game">
                    <form action="https://store.steampowered.com/freelicense/addfreelicense/" method="POST">
                      <input name="subid" value="1706211">
                    </form>
                    <p class="game_purchase_discount_quantity">
                      Get it by Aug 9 @ 10:00am. Keep it forever.
                    </p>
                  </div>
                </div></body></html>
            """.trimIndent(),
            nowMillis = now,
            zoneId = zone
        )

        assertEquals(SteamFreebieOfferKind.KEEP_FOREVER, item.offerKind)
        assertEquals(SteamFreebieClaimMethod.FREE_LICENSE, item.claimMethod)
        assertEquals(1706211, item.packageId)
        assertEquals(SteamFreebieProductType.GAME, item.productType)
        assertEquals("https://cdn.example/header.jpg", item.imageUrl)
        assertEquals(
            ZonedDateTime.of(2026, 8, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli(),
            item.endsAtEpochMillis
        )
    }

    @Test
    fun offerPageUsesSteamServerSessionIdForClaimRequests() {
        val html = """
                <form action="/freelicense/addfreelicense/">
                  <input name="snr" value="1_5_9__403">
                  <input name="originating_snr" value="">
                  <input name="action" value="add_to_cart">
                  <input type="hidden" name="sessionid" value="a4d2cb9bee17a1711e355aa0">
                  <input name="subid" value="1759598">
                </form>
            """.trimIndent()
        val sessionId = SteamFreebieOfferPageParser.parseSessionId(html)

        assertEquals("a4d2cb9bee17a1711e355aa0", sessionId)
        val form = SteamFreebieOfferPageParser.parseClaimForm(html)
        assertEquals("1_5_9__403", form?.snr)
        assertEquals("", form?.originatingSnr)
        assertEquals("add_to_cart", form?.action)
        assertEquals(1759598, form?.packageId)
    }

    @Test
    fun offerPageSeparatesOfficialCheckoutAndDlcBaseGame() {
        val item = SteamFreebieOfferPageParser.parse(
            candidate = candidate(discountPercent = 100),
            html = """
                <div class="game_area_dlc_bubble">
                  <a href="https://store.steampowered.com/app/1234/Base_Game/">Base Game</a>
                </div>
                <div class="game_area_purchase_game">
                  <div class="discount_block" data-price-final="0"></div>
                  <form action="https://store.steampowered.com/cart/">
                    <input name="subid" value="9876">
                  </form>
                </div>
            """.trimIndent()
        )

        assertEquals(SteamFreebieOfferKind.KEEP_FOREVER, item.offerKind)
        assertEquals(SteamFreebieClaimMethod.OFFICIAL_CHECKOUT, item.claimMethod)
        assertEquals(SteamFreebieProductType.DLC, item.productType)
        assertEquals(1234, item.baseGameAppId)
        assertEquals(9876, item.packageId)
    }

    @Test
    fun temporaryOfferWithoutLicenseStaysFreeWeekend() {
        val item = SteamFreebieOfferPageParser.parse(
            candidate = candidate(discountPercent = 100),
            html = """
                <div id="game_area_purchase">
                  <div class="game_area_purchase_game">
                    <p class="game_purchase_discount_quantity">Play for free this weekend.</p>
                  </div>
                </div>
            """.trimIndent()
        )

        assertEquals(SteamFreebieOfferKind.FREE_WEEKEND, item.offerKind)
        assertEquals(SteamFreebieClaimMethod.NONE, item.claimMethod)
        assertNull(item.packageId)
    }

    @Test
    fun submissionClassifierDistinguishesLoginAndRateLimit() {
        assertEquals(
            SteamFreebieSubmissionStatus.SESSION_REQUIRED,
            classifySteamFreebieSubmission(302, "https://store.steampowered.com/login/")
        )
        assertEquals(
            SteamFreebieSubmissionStatus.RATE_LIMITED,
            classifySteamFreebieSubmission(429, null)
        )
        assertEquals(
            SteamFreebieSubmissionStatus.ACCEPTED,
            classifySteamFreebieSubmission(302, "https://store.steampowered.com/app/606150/")
        )
        assertTrue(classifySteamFreebieSubmission(500, null) == SteamFreebieSubmissionStatus.REJECTED)
    }

    private fun candidate(discountPercent: Int) = SteamFreebieCandidate(
        appId = 606150,
        name = "Moonlighter",
        imageUrl = "https://cdn.example/capsule.jpg",
        storeUrl = "https://store.steampowered.com/app/606150/Moonlighter/",
        originalPriceText = "¥70.00",
        finalPriceText = "¥0.00",
        discountPercent = discountPercent
    )
}
