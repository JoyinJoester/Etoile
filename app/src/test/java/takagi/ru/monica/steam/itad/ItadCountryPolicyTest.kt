package takagi.ru.monica.steam.itad

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadCountryPolicy

class ItadCountryPolicyTest {
    @Test
    fun usesUppercaseIsoCountryFromSteamAccount() {
        assertEquals("CN", ItadCountryPolicy.normalize("cn", "US"))
    }

    @Test
    fun fallsBackToDeviceCountryThenUs() {
        assertEquals("IN", ItadCountryPolicy.normalize("", "in"))
        assertEquals("US", ItadCountryPolicy.normalize("invalid", ""))
    }

    @Test
    fun normalizesLegacyUkCode() {
        assertEquals("GB", ItadCountryPolicy.normalize("uk", "US"))
    }
}
