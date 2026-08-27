package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreSystemRequirementsUiGuardTest {
    @Test
    fun requirementsUseAnIndependentAccessibleNonTruncatingComponent() {
        val component = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/requirements/ui/SteamStoreSystemRequirementsSection.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(component.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue(component.contains("SegmentedButton("))
        assertTrue(component.contains("SelectionContainer"))
        assertTrue(component.contains("requirements.recommended.isNotBlank()"))
        assertFalse(component.contains("TextOverflow.Ellipsis"))
        assertFalse(component.contains("maxLines ="))
        assertTrue(screen.contains("SteamStoreSystemRequirementsSection("))
        assertTrue(screen.contains("requirements = detail.systemRequirements"))
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
