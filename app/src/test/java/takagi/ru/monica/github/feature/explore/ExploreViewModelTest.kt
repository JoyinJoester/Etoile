package takagi.ru.monica.github.feature.explore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchType
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubUserSummary

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultTopicLoadsRealCuratedRepositories() = runTest(dispatcher) {
        val expected = repository(id = 7, fullName = "android/nowinandroid")
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(listOf(expected), null)) }

        val viewModel = ExploreViewModel(fake)
        advanceUntilIdle()

        assertEquals(listOf("stars:>1000 sort:stars-desc" to 1), fake.queries)
        assertEquals(listOf(expected), viewModel.state.value.repositories)
        assertTrue(viewModel.state.value.isCurated)
    }

    @Test
    fun queryIsDebouncedAndPublishesSuccessfulResults() = runTest(dispatcher) {
        val expected = repository(id = 42, fullName = "openai/codex")
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(listOf(expected), null)) }
        val viewModel = ExploreViewModel(fake)

        viewModel.onAction(ExploreAction.QueryChanged("codex"))
        advanceTimeBy(349)
        assertTrue(fake.queries.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("codex" to 1), fake.queries)
        assertEquals(listOf(expected), viewModel.state.value.repositories)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun newerQueryCancelsOlderPendingSearch() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) }
        val viewModel = ExploreViewModel(fake)

        viewModel.onAction(ExploreAction.QueryChanged("compose"))
        advanceTimeBy(200)
        viewModel.onAction(ExploreAction.QueryChanged("kotlin"))
        advanceUntilIdle()

        assertEquals(listOf("kotlin" to 1), fake.queries)
    }

    @Test
    fun failureStopsLoadingAndExposesRecoverableError() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.failure(IllegalStateException("rate limited")) }
        val viewModel = ExploreViewModel(fake)

        viewModel.onAction(ExploreAction.QueryChanged("android"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.error)
        assertTrue(viewModel.state.value.repositories.isEmpty())
    }

    @Test
    fun loadMoreAppendsTheNextSearchPage() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, page ->
            Result.success(
                GithubPage(
                    items = listOf(repository(page.toLong(), "joyins/repo-$page")),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }
        val viewModel = ExploreViewModel(fake)
        advanceUntilIdle()

        viewModel.onAction(ExploreAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.repositories.map(GithubRepository::id))
        assertEquals(listOf(1, 2), fake.queries.map { it.second })
    }

    @Test
    fun usersSearchUsesTheSelectedGlobalSearchKind() = runTest(dispatcher) {
        val global = FakeGlobalSearchRepository()
        val viewModel = ExploreViewModel(
            repository = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) },
            globalSearch = global
        )
        advanceUntilIdle()

        viewModel.onAction(ExploreAction.SearchKindSelected(ExploreSearchKind.USERS))
        viewModel.onAction(ExploreAction.QueryChanged("joy"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf("joy" to 1), global.userQueries)
        assertEquals(listOf("joyins"), viewModel.state.value.users.map { it.login })
        assertTrue(viewModel.state.value.repositories.isEmpty())
    }

    @Test
    fun issuesSearchUsesDedicatedLightweightResults() = runTest(dispatcher) {
        val global = FakeGlobalSearchRepository()
        val viewModel = ExploreViewModel(
            repository = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) },
            globalSearch = global
        )
        advanceUntilIdle()

        viewModel.onAction(ExploreAction.SearchKindSelected(ExploreSearchKind.ISSUES))
        viewModel.onAction(ExploreAction.QueryChanged("crash"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf("crash" to 1), global.issueQueries)
        assertEquals(listOf(17), viewModel.state.value.conversations.map { it.number })
        assertEquals(GithubIssueSearchType.ISSUE, viewModel.state.value.conversations.single().type)
        assertTrue(viewModel.state.value.repositories.isEmpty())
    }

    @Test
    fun pullRequestSearchPaginatesWithoutMixingOtherSearchKinds() = runTest(dispatcher) {
        val global = FakeGlobalSearchRepository()
        val viewModel = ExploreViewModel(
            repository = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) },
            globalSearch = global
        )
        advanceUntilIdle()

        viewModel.onAction(ExploreAction.SearchKindSelected(ExploreSearchKind.PULL_REQUESTS))
        viewModel.onAction(ExploreAction.QueryChanged("native"))
        advanceUntilIdle()
        viewModel.onAction(ExploreAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("native" to 1, "native" to 2), global.pullRequestQueries)
        assertEquals(listOf(1L, 2L), viewModel.state.value.conversations.map { it.id })
        assertTrue(viewModel.state.value.users.isEmpty())
        assertTrue(viewModel.state.value.code.isEmpty())
    }

    private class FakeSearchRepository(
        private val result: suspend (String, Int) -> Result<GithubPage<GithubRepository>>
    ) : GithubRepositorySearchRepository {
        val queries = mutableListOf<Pair<String, Int>>()

        override suspend fun search(query: String, page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
            queries += query to page
            return result(query, page)
        }
    }

    private class FakeGlobalSearchRepository : GithubGlobalSearchRepository {
        val userQueries = mutableListOf<Pair<String, Int>>()
        val issueQueries = mutableListOf<Pair<String, Int>>()
        val pullRequestQueries = mutableListOf<Pair<String, Int>>()

        override suspend fun users(query: String, page: Int, perPage: Int) = run {
            userQueries += query to page
            Result.success(
                GithubPage(
                    listOf(GithubUserSearchResult(8, "joyins", null, "https://github.com/joyins", "User")),
                    null
                )
            )
        }

        override suspend fun code(query: String, page: Int, perPage: Int) =
            Result.success<GithubPage<GithubCodeSearchResult>>(GithubPage(emptyList(), null))

        override suspend fun issues(query: String, page: Int, perPage: Int) = run {
            issueQueries += query to page
            Result.success(GithubPage(listOf(conversation(17L, 17, GithubIssueSearchType.ISSUE)), null))
        }

        override suspend fun pullRequests(query: String, page: Int, perPage: Int) = run {
            pullRequestQueries += query to page
            Result.success(
                GithubPage(
                    listOf(conversation(page.toLong(), page, GithubIssueSearchType.PULL_REQUEST)),
                    if (page == 1) 2 else null
                )
            )
        }
    }

    private fun repository(id: Long, fullName: String) = GithubRepository(
        id = id,
        name = fullName.substringAfter('/'),
        fullName = fullName,
        description = "Description",
        language = "Kotlin",
        stars = 100,
        updatedAt = "2026-08-16",
        isPrivate = false,
        htmlUrl = "https://github.com/$fullName"
    )

    private companion object {
        fun conversation(id: Long, number: Int, type: GithubIssueSearchType) = GithubIssueSearchResult(
            id = id,
            number = number,
            title = if (type == GithubIssueSearchType.ISSUE) "Crash" else "Native client",
            state = GithubIssueState.OPEN,
            type = type,
            isDraft = type == GithubIssueSearchType.PULL_REQUEST,
            author = GithubUserSummary("alice", null, "https://github.com/alice"),
            labels = emptyList(),
            comments = 1,
            repositoryFullName = "openai/codex",
            createdAt = "2026-08-16T00:00:00Z",
            updatedAt = "2026-08-17T00:00:00Z",
            htmlUrl = if (type == GithubIssueSearchType.ISSUE) {
                "https://github.com/openai/codex/issues/$number"
            } else {
                "https://github.com/openai/codex/pull/$number"
            }
        )
    }
}
