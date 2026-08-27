package takagi.ru.monica.github.feature.mywork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchType
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserSearchResult
import takagi.ru.monica.github.domain.GithubUserSummary

@OptIn(ExperimentalCoroutinesApi::class)
class MyConversationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun issuesQueryInvolvesViewerAndPaginates() = runTest(dispatcher) {
        val repository = FakeSearchRepository()
        val viewModel = MyConversationsViewModel(repository, MyConversationsKind.ISSUES)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        assertEquals(listOf("is:open involves:joyins"), repository.issueQueries.map { it.first })
        assertEquals(listOf(1), repository.issueQueries.map { it.second })
        assertTrue(repository.pullRequestQueries.isEmpty())
        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubIssueSearchResult::id))

        viewModel.onAction(MyConversationsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.issueQueries.map { it.second })
        assertEquals(listOf(1L, 2L, 3L), viewModel.state.value.items.map(GithubIssueSearchResult::id))
    }

    @Test
    fun pullRequestsQueryInvolvesViewer() = runTest(dispatcher) {
        val repository = FakeSearchRepository()
        val viewModel = MyConversationsViewModel(repository, MyConversationsKind.PULL_REQUESTS)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        assertEquals(listOf("is:open involves:joyins"), repository.pullRequestQueries.map { it.first })
        assertTrue(repository.issueQueries.isEmpty())
    }

    @Test
    fun signedOutSessionRequiresAuthenticationAndClearsItems() = runTest(dispatcher) {
        val repository = FakeSearchRepository()
        val viewModel = MyConversationsViewModel(repository, MyConversationsKind.ISSUES)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedOut)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.requiresAuthentication)
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun failureSetsErrorState() = runTest(dispatcher) {
        val repository = FakeSearchRepository(failure = true)
        val viewModel = MyConversationsViewModel(repository, MyConversationsKind.ISSUES)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error)
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    private fun signedInSession(login: String) = GithubSession.SignedIn(
        GithubAccount(
            id = 1,
            login = login,
            name = null,
            bio = null,
            avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
            htmlUrl = "https://github.com/$login",
            publicRepositories = 0,
            followers = 0,
            following = 0
        )
    )

    private class FakeSearchRepository(
        private val failure: Boolean = false
    ) : GithubGlobalSearchRepository {
        val issueQueries = mutableListOf<Pair<String, Int>>()
        val pullRequestQueries = mutableListOf<Pair<String, Int>>()
        private var issuePage = 0

        override suspend fun users(query: String, page: Int, perPage: Int) =
            Result.success(GithubPage(emptyList<GithubUserSearchResult>(), null))

        override suspend fun code(query: String, page: Int, perPage: Int) =
            Result.success(GithubPage(emptyList<GithubCodeSearchResult>(), null))

        override suspend fun issues(query: String, page: Int, perPage: Int): Result<GithubPage<GithubIssueSearchResult>> {
            issueQueries += query to page
            return response(page, GithubIssueSearchType.ISSUE)
        }

        override suspend fun pullRequests(query: String, page: Int, perPage: Int): Result<GithubPage<GithubIssueSearchResult>> {
            pullRequestQueries += query to page
            return response(page, GithubIssueSearchType.PULL_REQUEST)
        }

        private fun response(page: Int, type: GithubIssueSearchType): Result<GithubPage<GithubIssueSearchResult>> {
            if (failure) return Result.failure(IllegalStateException("boom"))
            issuePage += 1
            val items = when (page) {
                1 -> listOf(result(1), result(2))
                else -> listOf(result(3))
            }
            return Result.success(GithubPage(items, if (page == 1) 2 else null))
        }

        private fun result(id: Long) = GithubIssueSearchResult(
            id = id,
            number = id.toInt(),
            title = "Conversation $id",
            state = GithubIssueState.OPEN,
            type = GithubIssueSearchType.ISSUE,
            isDraft = false,
            author = GithubUserSummary("joyins", null, "https://github.com/joyins"),
            labels = listOf(GithubIssueLabel("bug", "d73a4a", null)),
            comments = 0,
            repositoryFullName = "joyins/etoile",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-02T00:00:00Z",
            htmlUrl = "https://github.com/joyins/etoile/issues/1"
        )
    }
}
