package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption

class SteamStoreFreeLicenseModelTest {
    @Test
    fun detailExposesTheClaimableFreeLicensePackage() {
        val option = SteamStorePackageOption(
            packageId = 1759598,
            priceCents = 0,
            canGetFreeLicense = true
        )
        val detail = SteamStoreDetail(
            appId = 738520,
            name = "呼吸边缘",
            isFree = true,
            packageOptions = listOf(option)
        )

        assertEquals(option, detail.freeLicenseOption)
    }

    @Test
    fun commercialPackageIsNotMistakenForAFreeLicense() {
        val detail = SteamStoreDetail(
            appId = 123,
            name = "Commercial",
            packageOptions = listOf(
                SteamStorePackageOption(packageId = 999, priceCents = 34900)
            )
        )

        assertNull(detail.freeLicenseOption)
    }
}
