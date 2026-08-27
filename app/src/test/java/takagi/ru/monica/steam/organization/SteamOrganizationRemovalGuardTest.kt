package takagi.ru.monica.steam.organization

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOrganizationRemovalGuardTest {
    @Test
    fun organizationUiAndMutationPathsStayRemovedWhileStorageRemainsCompatible() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()
        val repository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        val accountModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountEntity.kt"
        ).readText()

        assertFalse(screen.contains("SteamOrganization"))
        assertFalse(screen.contains("onEditOrganization"))
        assertFalse(viewModel.contains("fun updateOrganization("))
        assertFalse(repository.contains("suspend fun updateOrganization("))
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/organization/" +
                    "SteamAccountOrganization.kt"
            ).exists()
        )
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/organization/ui/" +
                    "SteamOrganizationComponents.kt"
            ).exists()
        )
        listOf("groupName", "tags", "accentArgb", "note", "pinned").forEach { field ->
            assertTrue(accountModel.contains(field))
        }
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
