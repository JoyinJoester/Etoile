package takagi.ru.monica.github.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryCreateDraft
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoriesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun repositoriesAppendPagesAndKeepStableIds() = runTest(dispatcher) {
        val viewModel = UserRepositoriesViewModel(FakeRepository())
        advanceUntilIdle()
        viewModel.onAction(UserRepositoriesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubRepository::id))
    }

    private class FakeRepository : GithubUserRepositoriesRepository {
        override suspend fun repositories(page: Int, perPage: Int) = Result.success(
            GithubPage(
                items = listOf(repository(page.toLong())),
                nextPage = if (page == 1) 2 else null
            )
        )

        override suspend fun create(draft: GithubRepositoryCreateDraft) =
            Result.failure<GithubRepository>(UnsupportedOperationException("this screen never creates"))
    }

    @Test
    fun retryAfterFailedRefreshRequestsFirstPageAndKeepsRows() = runTest(dispatcher) {
        val requested = mutableListOf<Int>()
        var fail = false
        val source = object : GithubUserRepositoriesRepository {
            override suspend fun repositories(page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
                requested += page
                return if (fail) Result.failure(IllegalStateException("Offline"))
                else Result.success(GithubPage(listOf(repository(1)), nextPage = null))
            }

            override suspend fun create(draft: GithubRepositoryCreateDraft) =
                Result.failure<GithubRepository>(UnsupportedOperationException("this screen never creates"))
        }
        val viewModel = UserRepositoriesViewModel(source)
        advanceUntilIdle()
        fail = true
        viewModel.onAction(UserRepositoriesAction.Refresh)
        advanceUntilIdle()
        fail = false
        viewModel.onAction(UserRepositoriesAction.Retry)
        assertEquals(listOf(1L), viewModel.state.value.items.map { it.id })
        advanceUntilIdle()
        assertEquals(listOf(1, 1, 1), requested)
        assertEquals(false, viewModel.state.value.error)
    }

    @Test
    fun retryAfterFailedPaginationRequestsSamePage() = runTest(dispatcher) {
        val requested = mutableListOf<Int>()
        var fail = true
        val source = object : GithubUserRepositoriesRepository {
            override suspend fun repositories(page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
                requested += page
                return if (page == 2 && fail) Result.failure(IllegalStateException("Offline"))
                else Result.success(GithubPage(listOf(repository(page.toLong())), nextPage = if (page == 1) 2 else null))
            }

            override suspend fun create(draft: GithubRepositoryCreateDraft) =
                Result.failure<GithubRepository>(UnsupportedOperationException("this screen never creates"))
        }
        val viewModel = UserRepositoriesViewModel(source)
        advanceUntilIdle()
        viewModel.onAction(UserRepositoriesAction.LoadMore)
        advanceUntilIdle()
        fail = false
        viewModel.onAction(UserRepositoriesAction.Retry)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), requested)
        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map { it.id })
    }

    private companion object {
        fun repository(id: Long) = GithubRepository(
            id = id,
            name = "repo-$id",
            fullName = "joyins/repo-$id",
            description = null,
            language = "Kotlin",
            stars = id.toInt(),
            updatedAt = "2026-08-16T00:00:00Z",
            isPrivate = id == 2L,
            htmlUrl = "https://github.com/joyins/repo-$id"
        )
    }
}
