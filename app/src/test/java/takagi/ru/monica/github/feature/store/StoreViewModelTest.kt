package takagi.ru.monica.github.feature.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = GithubAccount(1, "first", null, null, "", "", 0, 0, 0)
    private val second = first.copy(id = 2, login = "second")
    private val repo = GithubRepository(8, "app", "team/app", "private description", null, 1000, null, true, "https://github.com/team/app")

    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun accountSwitchClearsPrivateSelectionAndDoesNotReusePreviousReleaseCache() = runTest(dispatcher) {
        val sessions = MutableStateFlow<GithubSession>(GithubSession.SignedIn(first))
        val releases = Releases()
        val search = Search { Result.success(GithubPage(listOf(repo), null)) }
        val vm = StoreViewModel(search, releases, sessions = sessions, ioDispatcher = dispatcher, computationDispatcher = dispatcher)
        runCurrent(); vm.onAction(StoreAction.CatalogTabSelected(StoreCatalogTab.RECOMMENDED)); runCurrent()
        vm.onAction(StoreAction.OpenApp(repo)); runCurrent()
        assertEquals(1L, vm.state.value.selected?.latestRelease?.id)
        vm.onAction(StoreAction.SourceInputChanged("private/next-app"))
        releases.block = { _, _ -> Result.success(GithubPage(listOf(release(2)), null)) }
        sessions.value = GithubSession.SignedIn(second); runCurrent()
        assertNull(vm.state.value.selected)
        assertEquals("", vm.state.value.sourceInput)
        assertTrue(vm.state.value.downloads.isEmpty())
        vm.onAction(StoreAction.OpenApp(repo)); runCurrent()
        assertEquals(2L, vm.state.value.selected?.latestRelease?.id)
        assertEquals(2, releases.calls)
        sessions.value = GithubSession.Loading; runCurrent()
        assertTrue(vm.state.value.apps.isEmpty())
        assertNull(vm.state.value.selected)
        vm.onAction(StoreAction.OpenApp(repo)); runCurrent()
        assertNull(vm.state.value.selected)
    }

    @Test fun lateCatalogResponseFromPreviousAccountCannotRepopulatePrivateContent() = runTest(dispatcher) {
        val sessions = MutableStateFlow<GithubSession>(GithubSession.SignedIn(first))
        val old = CompletableDeferred<Result<GithubPage<GithubRepository>>>()
        var call = 0
        val search = Search {
            call++
            if (call == 1) withContext(NonCancellable) { old.await() } else Result.success(GithubPage(emptyList(), null))
        }
        val vm = StoreViewModel(search, Releases(), sessions = sessions, ioDispatcher = dispatcher, computationDispatcher = dispatcher)
        runCurrent(); vm.onAction(StoreAction.CatalogTabSelected(StoreCatalogTab.RECOMMENDED)); runCurrent()
        sessions.value = GithubSession.SignedOut; runCurrent()
        old.complete(Result.success(GithubPage(listOf(repo), null))); runCurrent()
        assertTrue(vm.state.value.apps.isEmpty())
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.selected)
    }

    @Test fun lateClosedDetailCannotAttachItsReleaseToAnotherRepository() = runTest(dispatcher) {
        val another = repo.copy(id = 9, name = "another", fullName = "team/another")
        val search = Search { Result.success(GithubPage(listOf(repo, another), null)) }
        val releases = Releases()
        val vm = StoreViewModel(search, releases, ioDispatcher = dispatcher, computationDispatcher = dispatcher)
        runCurrent(); vm.onAction(StoreAction.CatalogTabSelected(StoreCatalogTab.RECOMMENDED)); runCurrent()
        val oldDetail = CompletableDeferred<Result<GithubPage<GithubRelease>>>()
        search.block = { CompletableDeferred<Result<GithubPage<GithubRepository>>>().await() }
        vm.onAction(StoreAction.Refresh); runCurrent()
        releases.block = { _, name -> if (name == "app") withContext(NonCancellable) { oldDetail.await() }
            else Result.success(GithubPage(listOf(release(9)), null)) }
        vm.onAction(StoreAction.OpenApp(repo)); runCurrent()
        vm.onAction(StoreAction.OpenApp(another)); runCurrent()
        oldDetail.complete(Result.success(GithubPage(listOf(release(1)), null))); runCurrent()
        assertEquals(another, vm.state.value.selected?.repository)
        assertEquals(9L, vm.state.value.selected?.latestRelease?.id)
        assertFalse(vm.state.value.isLoadingDetail)
    }

    private class Search(var block: suspend () -> Result<GithubPage<GithubRepository>>) : GithubRepositorySearchRepository {
        override suspend fun search(query: String, page: Int, perPage: Int) = block()
    }
    private inner class Releases : GithubReleasesRepository {
        var calls = 0
        var block: suspend (String, String) -> Result<GithubPage<GithubRelease>> = { _, _ -> Result.success(GithubPage(listOf(release(1)), null)) }
        override suspend fun releases(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubRelease>> { calls++; return block(owner, name) }
        override suspend fun release(owner: String, name: String, releaseId: Long) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun releaseByTag(owner: String, name: String, tagName: String) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun createRelease(owner: String, name: String, draft: GithubReleaseDraft) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun updateRelease(owner: String, name: String, releaseId: Long, draft: GithubReleaseDraft) = Result.failure<GithubRelease>(UnsupportedOperationException())
        override suspend fun deleteRelease(owner: String, name: String, releaseId: Long) = Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun uploadAsset(owner: String, name: String, releaseId: Long, asset: GithubReleaseAssetUpload) = Result.failure<GithubReleaseAsset>(UnsupportedOperationException())
        override suspend fun deleteAsset(owner: String, name: String, assetId: Long) = Result.failure<Unit>(UnsupportedOperationException())
    }
    private fun release(id: Long) = GithubRelease(id, "v$id", "main", "Release $id", "", GithubUserSummary("author", null, ""),
        false, false, "", null, "https://github.com/team/app/releases/$id",
        listOf(GithubReleaseAsset(id, "app.apk", null, "application/vnd.android.package-archive", 1, 0, "", "https://example.com/app.apk")))
}
