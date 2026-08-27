package takagi.ru.monica.github.feature.repository

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
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryContentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun directoryLoadSortsFoldersBeforeFilesAndThenByName() = runTest(dispatcher) {
        val repository = FakeRepository()

        val viewModel = RepositoryFilesViewModel("openai", "codex", "main", "", repository)
        advanceUntilIdle()

        assertEquals(listOf("alpha", "zeta", "A.kt", "Z.kt"), viewModel.state.value.items.map { it.name })
        assertEquals(listOf("main", "release"), viewModel.state.value.branches.items.map { it.name })
        assertEquals(2, viewModel.state.value.branches.nextPage)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingBranches)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun selectingTagsLoadsThemLazilyAndKeepsBranchPage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryFilesViewModel("openai", "codex", "main", "", repository)
        advanceUntilIdle()

        assertEquals(0, repository.tagsCalls)
        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()

        assertEquals(1, repository.tagsCalls)
        assertEquals(listOf("v1.0.0"), viewModel.state.value.tags.items.map { it.name })
        assertEquals(2, viewModel.state.value.tags.nextPage)
        assertEquals(2, viewModel.state.value.branches.nextPage)
    }

    @Test
    fun referencePaginationAppendsWithStableDeduplication() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryFilesViewModel("openai", "codex", "main", "", repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.LoadMoreBranches)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.branchPages)
        assertEquals(listOf("main", "release", "hotfix"), viewModel.state.value.branches.items.map { it.name })
        assertEquals(null, viewModel.state.value.branches.nextPage)
    }

    @Test
    fun failedTagLoadCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository(
            tagsResult = Result.failure(IllegalStateException("offline"))
        )
        val viewModel = RepositoryFilesViewModel("openai", "codex", "main", "", repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tagsError)

        repository.tagsResult = Result.success(GithubPage(listOf(GithubTag("v1.0.0", "tag-sha")), null))
        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.tagsError)
        assertEquals("v1.0.0", viewModel.state.value.tags.items.single().name)
    }

    @Test
    fun fileLoadPublishesTypedContentAndSupportsRetry() = runTest(dispatcher) {
        val repository = FakeRepository(fileResult = Result.failure(IllegalStateException("offline")))
        val viewModel = RepositoryFileViewModel("openai", "codex", "main", "README.md", repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error)

        repository.fileResult = Result.success(GithubFileContent.Text("# README"))
        viewModel.onAction(RepositoryFileAction.Retry)
        advanceUntilIdle()

        assertEquals(GithubFileContent.Text("# README"), viewModel.state.value.content)
        assertFalse(viewModel.state.value.error)
    }

    private class FakeRepository(
        var fileResult: Result<GithubFileContent> = Result.success(GithubFileContent.Text("content")),
        var tagsResult: Result<GithubPage<GithubTag>> = Result.success(
            GithubPage(listOf(GithubTag("v1.0.0", "tag-sha")), nextPage = 2)
        )
    ) : GithubRepositoryContentsRepository {
        var tagsCalls = 0
        val branchPages = mutableListOf<Int>()

        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubBranch>> {
            branchPages += page
            return Result.success(
                if (page == 1) {
                    GithubPage(
                        items = listOf(
                            GithubBranch("main", "main-sha", isProtected = false),
                            GithubBranch("release", "release-sha", isProtected = true)
                        ),
                        nextPage = 2
                    )
                } else {
                    GithubPage(
                        items = listOf(
                            GithubBranch("release", "release-sha", isProtected = true),
                            GithubBranch("hotfix", "hotfix-sha", isProtected = false)
                        ),
                        nextPage = null
                    )
                }
            )
        }

        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubTag>> {
            tagsCalls += 1
            return tagsResult
        }

        override suspend fun directory(owner: String, name: String, path: String, ref: String?) =
            Result.success(
                listOf(
                    item("Z.kt", GithubContentType.FILE),
                    item("zeta", GithubContentType.DIRECTORY),
                    item("A.kt", GithubContentType.FILE),
                    item("alpha", GithubContentType.DIRECTORY)
                )
            )

        override suspend fun file(owner: String, name: String, path: String, ref: String?) = fileResult
    }

    private companion object {
        fun item(name: String, type: GithubContentType) = GithubContentItem(
            name = name,
            path = name,
            sha = "$name-sha",
            size = if (type == GithubContentType.FILE) 10 else 0,
            type = type,
            htmlUrl = null,
            downloadUrl = null
        )
    }
}
