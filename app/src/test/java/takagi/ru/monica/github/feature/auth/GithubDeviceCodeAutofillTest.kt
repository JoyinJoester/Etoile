package takagi.ru.monica.github.feature.auth

import org.junit.Assert.*
import org.junit.Test

class GithubDeviceCodeAutofillTest {
    @Test fun onlyDeviceAuthorizationPageCanReceiveTheCode() {
        assertTrue(GithubDeviceCodeAutofill.isDevicePage("https://github.com/login/device?return_to=x"))
        assertTrue(GithubDeviceCodeAutofill.isDevicePage("https://github.com/login/device/"))
        listOf(null, "http://github.com/login/device", "https://github.com.evil.test/login/device",
            "https://github.com@evil.test/login/device", "https://evil.test@github.com/login/device",
            "https://github.com:444/login/device", "https://github.com/login/device/other",
            "https://github.com/sessions/two-factor", "https://github.com/login",
            "https://github.com/login/%64evice", "not a URL").forEach {
            assertFalse(it, GithubDeviceCodeAutofill.isDevicePage(it))
        }
    }

    @Test fun acceptsOnlyEightAsciiLettersAndDigitsWithOptionalMiddleSeparator() {
        assertEquals("AB12CD34", GithubDeviceCodeAutofill.normalize(" ab12-cd34 "))
        assertEquals("AB12CD34", GithubDeviceCodeAutofill.normalize("AB12CD34"))
        listOf("", "AB12", "AB12-CD345", "AB-12CD34", "AB12 CD34", "AB12'CD3", "ＡB12-CD34").forEach {
            assertNull(GithubDeviceCodeAutofill.normalize(it))
        }
    }
}
