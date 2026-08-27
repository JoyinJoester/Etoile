package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamPredictiveBackGuardTest {
    @Test
    fun predictiveBackIsDisabledLikeMonicaPass() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"false\""))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
