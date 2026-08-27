package takagi.ru.monica.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveTopBarSearchBackGuardTest {
    @Test
    fun expandedSearchConsumesBackAndReturnsToTheCollapsedState() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/ExpressiveTopBar.kt"
        ).readText()
        val handler = source
            .substringAfter("BackHandler(enabled = isSearchExpanded)")
            .substringBefore("// 动画状态")

        assertTrue(source.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(handler.contains("onSearchExpandedChange(false)"))
        assertTrue(handler.contains("onSearchQueryChange(\"\")"))
        assertTrue(handler.contains("focusManager.clearFocus()"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
