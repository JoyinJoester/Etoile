package takagi.ru.monica.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.github.domain.GithubApkParser
import takagi.ru.monica.github.domain.GithubReleaseAsset

class GithubApkParserTest {

    private fun asset(name: String) = GithubReleaseAsset(
        id = 1L,
        name = name,
        label = null,
        contentType = "application/vnd.android.package-archive",
        sizeBytes = 1024L,
        downloadCount = 0,
        createdAt = "2026-01-01T00:00:00Z",
        downloadUrl = "https://example.com/$name"
    )

    @Test
    fun `parses version and abi from common names`() {
        val parsed = GithubApkParser.parse(asset("app-v1.2.3-arm64-v8a.apk"))
        assertEquals("1.2.3", parsed.version)
        assertEquals("arm64-v8a", parsed.abi)
    }

    @Test
    fun `parses version without v prefix`() {
        val parsed = GithubApkParser.parse(asset("MyApp-2.0.0.apk"))
        assertEquals("2.0.0", parsed.version)
        assertNull(parsed.abi)
    }

    @Test
    fun `parses multi digit versions with three components`() {
        val parsed = GithubApkParser.parse(asset("release-10.20.30-x86_64.apk"))
        assertEquals("10.20.30", parsed.version)
        assertEquals("x86_64", parsed.abi)
    }

    @Test
    fun `returns null version for opaque names`() {
        val parsed = GithubApkParser.parse(asset("app-release.apk"))
        assertNull(parsed.version)
        assertNull(parsed.abi)
    }

    @Test
    fun `parses universal abi`() {
        val parsed = GithubApkParser.parse(asset("myapp-1.0-universal.apk"))
        assertEquals("1.0", parsed.version)
        assertEquals("universal", parsed.abi)
    }

    @Test
    fun `prefers longest abi token`() {
        val parsed = GithubApkParser.parse(asset("app-1.0-armeabi-v7a.apk"))
        assertEquals("armeabi-v7a", parsed.abi)
    }
}
