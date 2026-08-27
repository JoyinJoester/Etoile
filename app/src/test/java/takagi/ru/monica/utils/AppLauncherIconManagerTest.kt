package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLauncherIconManagerTest {
    @Test
    fun skipsLauncherComponentsThatAreNotDeclared() {
        val declared = "declared"
        val missing = "missing"

        val result = AppLauncherIconManager.filterDeclaredLauncherComponents(
            components = listOf(declared, missing),
            isDeclared = { it == declared }
        )

        assertEquals(listOf(declared), result)
    }

    @Test
    fun ignoresPackageManagerLookupFailuresWhenFiltering() {
        val declared = "declared"
        val missing = "missing"

        val result = AppLauncherIconManager.filterDeclaredLauncherComponents(
            components = listOf(declared, missing),
            isDeclared = { component ->
                if (component == missing) error("component lookup failed")
                true
            }
        )

        assertEquals(listOf(declared), result)
    }
}
