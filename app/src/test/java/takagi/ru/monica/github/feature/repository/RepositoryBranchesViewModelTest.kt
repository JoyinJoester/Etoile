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
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubTag

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryBranchesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun branchesAppendWithoutDuplicatesAndCanBeFiltered() = runTest(dispatcher) {
        val viewModel = RepositoryBranchesViewModel("openai", "codex", "main", FakeRepository())
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.LoadMore)
        advanceUntilIdle()
        viewModel.onAction(RepositoryBranchesAction.Search("feature"))

        assertEquals(listOf("main", "feature/android"), viewModel.state.value.items.map(GithubBranch::name))
        assertEquals(listOf("feature/android"), viewModel.state.value.filteredItems.map(GithubBranch::name))
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    private class FakeRepository : GithubRepositoryContentsRepository {
        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) = when (page) {
            1 -> Result.success(
                GithubPage(
                    listOf(GithubBranch("main", "abc", true)),
                    nextPage = 2
                )
            )
            else -> Result.success(
                GithubPage(
                    listOf(
                        GithubBranch("main", "abc", true),
                        GithubBranch("feature/android", "def", false)
                    ),
                    nextPage = null
                )
            )
        }

        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubTag>(emptyList(), null))

        override suspend fun directory(owner: String, name: String, path: String, ref: String?) =
            Result.success(emptyList<GithubContentItem>())

        override suspend fun file(owner: String, name: String, path: String, ref: String?) =
            Result.success<GithubFileContent>(GithubFileContent.Text(""))
    }
}
