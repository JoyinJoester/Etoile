package takagi.ru.monica.steam.store.freebie.data

import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus

class SteamFreebieProtocolTest {
    @Test
    fun searchRequestUsesOfficialLimitedFreeFiltersAndAccountCountry() {
        val request = buildSteamFreebieSearchRequest(countryCode = "CN")

        assertEquals("store.steampowered.com", request.url.host)
        assertEquals("1", request.url.queryParameter("specials"))
        assertEquals("free", request.url.queryParameter("maxprice"))
        assertEquals("100", request.url.queryParameter("count"))
        assertEquals("CN", request.url.queryParameter("cc"))
    }

    @Test
    fun claimRequestPostsOfficialFreeLicenseFormWithAuthenticatedCookie() {
        val request = buildSteamFreebieClaimRequest(
            steamLoginSecure = "76561198000000000||token-value",
            packageId = 1706211,
            sessionId = "abcdef123456",
            storeUrl = "https://store.steampowered.com/app/606150/"
        )
        val body = request.body as FormBody

        assertEquals("POST", request.method)
        assertEquals("/freelicense/addfreelicense/", request.url.encodedPath)
        assertEquals("add_to_cart", body.formValue("action"))
        assertEquals("", body.formValue("originating_snr"))
        assertEquals("1706211", body.formValue("subid"))
        assertTrue(request.header("Cookie").orEmpty().contains("steamLoginSecure="))
        assertEquals("https://store.steampowered.com/app/606150/", request.header("Referer"))
    }

    @Test
    fun rejectedClaimMapsRegionAndBaseGameFailures() {
        assertEquals(
            SteamFreebieClaimStatus.REGION_RESTRICTED,
            classifyRejectedClaim("This item is not available in your region")
        )
        assertEquals(
            SteamFreebieClaimStatus.NEEDS_BASE_GAME,
            classifyRejectedClaim("You must own the base game before adding this DLC")
        )
        assertEquals(
            SteamFreebieClaimStatus.FAILED,
            classifyRejectedClaim("Unexpected response")
        )
    }

    @Test
    fun submissionClassifierTreatsHtmlLoginPageAsSessionRequired() {
        assertEquals(
            SteamFreebieSubmissionStatus.SESSION_REQUIRED,
            classifySteamFreebieSubmission(
                statusCode = 200,
                location = null,
                body = "<form id=login_form>Sign in to Steam</form>"
            )
        )
    }

    private fun FormBody.formValue(name: String): String =
        (0 until size).first { index -> this.name(index) == name }.let(::value)
}
