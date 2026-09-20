package takagi.ru.monica.github

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*

class StoreReleaseResolverTest {
    private fun release(id: Long, vararg names: String, draft: Boolean = false) = GithubRelease(
        id, "v$id", "main", null, null, GithubUserSummary("author", null, "https://github.com/author"),
        draft, false, "", null, "https://github.com/a/b/releases/$id",
        names.mapIndexed { i, name -> GithubReleaseAsset(id * 10 + i, name, null, "application/octet-stream", 1, 0, "", "https://example.com/$name") })
    private class Fake(val pages: Map<Int, GithubPage<GithubRelease>>) : GithubReleasesRepository {
        val calls = mutableListOf<Int>()
        override suspend fun releases(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubRelease>> {
            calls += page
            return pages[page]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("offline"))
        }
        override suspend fun release(owner: String, name: String, releaseId: Long) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun releaseByTag(owner: String, name: String, tagName: String) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun createRelease(owner: String, name: String, draft: GithubReleaseDraft) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun updateRelease(owner: String, name: String, releaseId: Long, draft: GithubReleaseDraft) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun deleteRelease(owner: String, name: String, releaseId: Long) = Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun uploadAsset(owner: String, name: String, releaseId: Long, asset: GithubReleaseAssetUpload) = Result.failure<GithubReleaseAsset>(UnsupportedOperationException())
        override suspend fun deleteAsset(owner: String, name: String, assetId: Long) = Result.failure<Unit>(UnsupportedOperationException())
    }
    @Test fun skipsDesktopReleaseAndFindsApkOnNextPage() = runTest {
        val api = Fake(mapOf(1 to GithubPage(listOf(release(3,"desktop.exe")),2),2 to GithubPage(listOf(release(2,"app.APK")),null)))
        assertEquals(2L, StoreReleaseResolver(api).resolve("a","b")?.id)
        assertEquals(listOf(1,2),api.calls)
    }
    @Test fun skipsIncompatibleAbiAndDraftAndStripsDesktopAssets() = runTest {
        val api = Fake(mapOf(1 to GithubPage(listOf(release(4,"app.apk",draft=true),release(3,"app-x86_64.apk"),release(2,"app-arm64-v8a.apk","desktop.zip")),null)))
        val found = StoreReleaseResolver(api,listOf("arm64-v8a")).resolve("a","b")!!
        assertEquals(2L,found.id)
        assertEquals(listOf("app-arm64-v8a.apk"),found.assets.map { it.name })
    }
    @Test fun absentApkIsNotAStoreApplication() = runTest {
        val api = Fake(mapOf(1 to GithubPage(listOf(release(1,"app.dmg")),null)))
        assertNull(StoreReleaseResolver(api).resolve("a","b"))
    }
    @Test fun networkFailureIsNotMisreportedAsNoApk() = runTest {
        val api = Fake(emptyMap())
        assertTrue(runCatching { StoreReleaseResolver(api).resolve("a","b") }.isFailure)
    }
    @Test fun universalApkWorksForOtherArchitectures() = runTest {
        val api = Fake(mapOf(1 to GithubPage(listOf(release(1,"app-universal.apk")),null)))
        assertNotNull(StoreReleaseResolver(api,listOf("x86_64")).resolve("a","b"))
    }
    @Test fun presetsHaveUniqueHttpsAddressesAndPinnedSignatures() {
        val sources = FdroidSources.builtIn
        assertTrue(sources.size >= 15)
        assertEquals(sources.size,sources.map { it.url }.distinct().size)
        assertTrue(sources.all { it.url.startsWith("https://") && it.fingerprint.matches(Regex("[a-fA-F0-9]{64}")) })
    }
    @Test fun installedAndUpdateFiltersRespectSearch() {
        val one = FdroidApp("one", "Reader", "feeds", "1", "a.apk", "https://example.com/a.apk", 1)
        val two = one.copy(packageName = "two", name = "Notes")
        val state = takagi.ru.monica.github.feature.store.StoreUiState(fdroidApps = listOf(one,two),
            fdroidInstalled = mapOf("one" to "0"), updates = listOf(one))
        assertEquals(listOf(one),state.copy(fdroidFilter = takagi.ru.monica.github.feature.store.FdroidCatalogFilter.INSTALLED).filteredFdroidApps)
        assertTrue(state.copy(fdroidFilter = takagi.ru.monica.github.feature.store.FdroidCatalogFilter.UPDATES, query = "notes").filteredFdroidApps.isEmpty())
        assertEquals(listOf(two),state.copy(query = "notes").filteredFdroidApps)
    }
}
