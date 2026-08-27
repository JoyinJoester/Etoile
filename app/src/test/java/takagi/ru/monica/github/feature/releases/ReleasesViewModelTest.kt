package takagi.ru.monica.github.feature.releases

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.GithubUserSummary

@OptIn(ExperimentalCoroutinesApi::class)
class ReleasesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listLoadsAndAppendsReleasePages() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubRelease::id))
        assertEquals(listOf(1, 2), repository.pages)
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun nextPageFailureKeepsLoadedReleasesAndCanRetry() = runTest(dispatcher) {
        val repository = FakeReleasesRepository().apply { failSecondPage = true }
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.state.value.items.map(GithubRelease::id))
        assertTrue(viewModel.state.value.error)

        repository.failSecondPage = false
        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubRelease::id))
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun detailLoadsReleaseIndependently() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleaseDetailViewModel(
            owner = "openai",
            name = "codex",
            reference = ReleaseReference.Id(7),
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(7L, viewModel.state.value.release?.id)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun tagDetailUsesTheTagEndpoint() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleaseDetailViewModel(
            owner = "openai",
            name = "codex",
            reference = ReleaseReference.Tag("preview/1.2"),
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(listOf("preview/1.2"), repository.tags)
        assertEquals("Release 12", viewModel.state.value.release?.name)
    }

    private class FakeReleasesRepository : GithubReleasesRepository {
        val pages = mutableListOf<Int>()
        val tags = mutableListOf<String>()
        var failSecondPage = false

        override suspend fun releases(
            owner: String,
            name: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubRelease>> {
            pages += page
            if (page == 2 && failSecondPage) {
                return Result.failure(IllegalStateException("page failed"))
            }
            return Result.success(
                GithubPage(
                    items = listOf(release(page.toLong())),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }

        override suspend fun release(owner: String, name: String, releaseId: Long) =
            Result.success(release(releaseId))

        override suspend fun releaseByTag(owner: String, name: String, tagName: String): Result<GithubRelease> {
            tags += tagName
            return Result.success(release(12))
        }
    }

    private companion object {
        fun release(id: Long) = GithubRelease(
            id = id,
            tagName = "v$id.0.0",
            targetCommitish = "main",
            name = "Release $id",
            body = "Highlights",
            author = GithubUserSummary("alice", null, "https://github.com/alice"),
            isDraft = false,
            isPrerelease = id == 1L,
            createdAt = "2026-08-15T00:00:00Z",
            publishedAt = "2026-08-16T00:00:00Z",
            htmlUrl = "https://github.com/openai/codex/releases/tag/v$id.0.0",
            assets = listOf(
                GithubReleaseAsset(
                    id = id * 10,
                    name = "asset-$id.zip",
                    label = null,
                    contentType = "application/zip",
                    sizeBytes = 1_024,
                    downloadCount = 3,
                    createdAt = "2026-08-16T00:00:00Z",
                    downloadUrl = "https://github.com/openai/codex/releases/download/v$id.0.0/asset-$id.zip"
                )
            )
        )
    }
}
