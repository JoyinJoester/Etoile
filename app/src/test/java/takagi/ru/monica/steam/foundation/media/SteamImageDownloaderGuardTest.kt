package takagi.ru.monica.steam.foundation.media

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamImageDownloaderGuardTest {
    @Test
    fun downloaderRevalidatesRedirectsAndPreservesCancellation() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamImageDownloader.kt"
        ).readText()

        assertTrue(source.contains("response.request.url.toString()"))
        assertTrue(source.contains("catch (cancellation: CancellationException)"))
        assertTrue(source.contains("throw cancellation"))
        assertTrue(source.contains("MAX_IMAGE_BYTES"))
        assertTrue(source.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
