package takagi.ru.monica.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.data.model.OtpType

class SteamGuardCodeGroupingTest {
    @Test
    fun steamGuardCodeCanBeGroupedOrContinuous() {
        assertEquals(
            "5X MGM",
            formatOtpCodeForDisplay("5XMGM", OtpType.STEAM, groupSteamCode = true)
        )
        assertEquals(
            "5XMGM",
            formatOtpCodeForDisplay("5XMGM", OtpType.STEAM, groupSteamCode = false)
        )
    }

    @Test
    fun steamGroupingPreferenceDoesNotChangeNumericTotpFormatting() {
        assertEquals(
            "123 456",
            formatOtpCodeForDisplay("123456", OtpType.TOTP, groupSteamCode = false)
        )
    }
}
