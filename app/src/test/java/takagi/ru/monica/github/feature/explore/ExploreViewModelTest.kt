package takagi.ru.monica.github.feature.explore

import androidx.lifecycle.SavedStateHandle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    fun restoredQueryLoadsFirstPageInsteadOfDefaultRecommendations() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) }
        val handle = SavedStateHandle()
        val original = ExploreViewModel(fake, savedStateHandle = handle)
        original.onAction(ExploreAction.QueryChanged(" language:kotlin stars:>1000 "))
        advanceUntilIdle()
        fake.queries.clear()
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = ExploreViewModel(fake, savedStateHandle = restoredHandle)
        advanceUntilIdle()
        assertEquals(" language:kotlin stars:>1000 ", restored.state.value.query)
        assertEquals(listOf("language:kotlin stars:>1000" to 1), fake.queries)
    }

    @Test
    fun restoredEmptyUserSearchDoesNotRequestRepositoryRecommendations() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) }
        val handle = SavedStateHandle(mapOf("searchKind" to "USERS"))
        val restored = ExploreViewModel(fake, savedStateHandle = handle)
        advanceUntilIdle()
        assertEquals(ExploreSearchKind.USERS, restored.state.value.searchKind)
        assertFalse(restored.state.value.isLoading)
        assertTrue(fake.queries.isEmpty())
    }

    @Test
    fun topicSelectionSurvivesRecreation() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) }
        val handle = SavedStateHandle()
        val original = ExploreViewModel(fake, savedStateHandle = handle)
        original.onAction(ExploreAction.TopicSelected(ExploreTopic.COMPOSE))
        advanceUntilIdle()
        fake.queries.clear()
        val restored = ExploreViewModel(fake, savedStateHandle = SavedStateHandle(
            handle.keys().associateWith { handle.get<Any?>(it) }
        ))
        advanceUntilIdle()
        assertEquals(ExploreTopic.COMPOSE, restored.state.value.selectedTopic)
        assertEquals(listOf(ExploreTopic.COMPOSE.query() to 1), fake.queries)
    }

    @Test
    fun obsoleteSavedFiltersFallBackWithoutDiscardingQuery() = runTest(dispatcher) {
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(emptyList(), null)) }
        val restored = ExploreViewModel(fake, savedStateHandle = SavedStateHandle(mapOf(
            "query" to "compose", "searchKind" to "REMOVED_KIND", "topic" to "REMOVED_TOPIC"
        )))
        advanceUntilIdle()
        assertEquals(ExploreSearchKind.REPOSITORIES, restored.state.value.searchKind)
        assertEquals(ExploreTopic.FOR_YOU, restored.state.value.selectedTopic)
        assertEquals(listOf("compose" to 1), fake.queries)
    }

    @Test
    fun defaultTopicLoadsRealCuratedRepositories() = runTest(dispatcher) {
        val expected = repository(id = 7, fullName = "android/nowinandroid")
        val fake = FakeSearchRepository { _, _ -> Result.success(GithubPage(listOf(expected), null)) }

        val viewModel = ExploreViewModel(fake)
        advanceUntilIdle()

        // FOR_YOU 跟踪近 30 天冒出的新星仓库：created:>date stars:>50 sort:stars-desc
        val (query, page) = fake.queries.single()
        assertTrue("expected a created:> date filter, got: $query", query.startsWith("created:>"))
        assertTrue(query.endsWith("stars:>50 sort:stars-desc"))
        assertEquals(1, page)
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

    @Test
    fun switchingAccountsClearsPrivateResultsAndReloadsThePreservedQuery() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository, savedStateHandle = SavedStateHandle(mapOf("query" to " compose ")))
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.requests[0].complete(listOf(repository(1, "alice/private-compose").copy(isPrivate = true)), 2)
        runCurrent()

        viewModel.onSessionChanged(signedIn(2, "bob"))
        assertEquals(" compose ", viewModel.state.value.query)
        assertEquals(ExploreSearchKind.REPOSITORIES, viewModel.state.value.searchKind)
        assertTrue(viewModel.state.value.repositories.isEmpty())
        assertEquals(null, viewModel.state.value.nextPage)
        assertTrue(viewModel.state.value.isLoading)
        runCurrent()
        assertEquals(listOf("compose" to 1, "compose" to 1), repository.requests.map { it.query to it.page })
        repository.requests[1].complete(listOf(repository(2, "bob/compose")))
        runCurrent()
        assertEquals(listOf("bob/compose"), viewModel.state.value.repositories.map { it.fullName })
    }

    @Test
    fun logoutClearsPrivateResultsAndDiscardsLatePaginationFromTheOldAccount() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository, savedStateHandle = SavedStateHandle(mapOf("query" to "compose")))
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.requests[0].complete(listOf(repository(1, "alice/private-compose").copy(isPrivate = true)), 2)
        runCurrent()
        viewModel.onAction(ExploreAction.LoadMore)
        runCurrent()

        viewModel.onSessionChanged(GithubSession.SignedOut)
        assertTrue(viewModel.state.value.repositories.isEmpty())
        assertEquals(null, viewModel.state.value.nextPage)
        assertFalse(viewModel.state.value.isLoadingMore)
        runCurrent()
        repository.requests[2].complete(listOf(repository(3, "public/compose")))
        runCurrent()
        repository.requests[1].complete(listOf(repository(2, "alice/another-private").copy(isPrivate = true)), 3)
        runCurrent()

        assertEquals(listOf("public/compose"), viewModel.state.value.repositories.map { it.fullName })
        assertEquals("compose", viewModel.state.value.query)
        assertEquals(null, viewModel.state.value.nextPage)
        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun lateFirstPageFromTheOldAccountCannotReplaceCurrentResults() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.onSessionChanged(signedIn(2, "bob"))
        runCurrent()
        repository.requests[1].complete(listOf(repository(2, "bob/current")))
        runCurrent()
        repository.requests[0].complete(listOf(repository(1, "alice/previous")))
        runCurrent()

        assertEquals(listOf("bob/current"), viewModel.state.value.repositories.map { it.fullName })
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun unresolvedSessionPausesRequestsAndResumesTheSelectedTopic() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onSessionChanged(GithubSession.Loading)
        viewModel.onAction(ExploreAction.TopicSelected(ExploreTopic.COMPOSE))
        runCurrent()
        assertTrue(repository.requests.isEmpty())
        assertTrue(viewModel.state.value.repositories.isEmpty())

        val session = signedIn(1, "alice")
        viewModel.onSessionChanged(session)
        runCurrent()
        assertEquals(ExploreTopic.COMPOSE.query(), repository.requests.single().query)
        repository.requests[0].complete(listOf(repository(1, "android/compose")))
        runCurrent()
        viewModel.onSessionChanged(session.copy(account = session.account.copy(followers = 2)))
        runCurrent()
        assertEquals(1, repository.requests.size)
        assertEquals(ExploreTopic.COMPOSE, viewModel.state.value.selectedTopic)
    }

    @Test
    fun switchingToRepositoryScopeShowsLoadingThroughoutTheDebounceAndRequest() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(
            repository,
            FakeGlobalSearchRepository(),
            SavedStateHandle(mapOf("query" to "codex", "searchKind" to "USERS"))
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)

        viewModel.onAction(ExploreAction.SearchKindSelected(ExploreSearchKind.REPOSITORIES))
        assertTrue(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.users.isEmpty())
        advanceTimeBy(349)
        assertTrue(viewModel.state.value.isLoading)
        assertTrue(repository.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("codex", repository.requests.single().query)
        assertTrue(viewModel.state.value.isLoading)
        repository.requests[0].complete(listOf(repository(42, "openai/codex")))
        runCurrent()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("openai/codex"), viewModel.state.value.repositories.map { it.fullName })
    }

    @Test
    fun lateFailureFromCancelledQueryDoesNotReplaceNewResultsWithAnError() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository)
        viewModel.onAction(ExploreAction.QueryChanged("old"))
        advanceTimeBy(350)
        runCurrent()
        viewModel.onAction(ExploreAction.QueryChanged("new"))
        advanceTimeBy(350)
        runCurrent()
        repository.requests[1].complete(listOf(repository(2, "public/new")))
        runCurrent()
        repository.requests[0].fail()
        runCurrent()

        assertEquals("new", viewModel.state.value.query)
        assertEquals(listOf("public/new"), viewModel.state.value.repositories.map { it.fullName })
        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun lateRepositoryResponseCannotPopulateTheSelectedUserScope() = runTest(dispatcher) {
        val repository = ControlledSearchRepository()
        val viewModel = ExploreViewModel(repository, FakeGlobalSearchRepository())
        runCurrent()
        viewModel.onAction(ExploreAction.SearchKindSelected(ExploreSearchKind.USERS))
        viewModel.onAction(ExploreAction.QueryChanged("joy"))
        advanceTimeBy(350)
        runCurrent()
        repository.requests[0].complete(listOf(repository(1, "previous/repository")))
        runCurrent()

        assertEquals(ExploreSearchKind.USERS, viewModel.state.value.searchKind)
        assertEquals(listOf("joyins"), viewModel.state.value.users.map { it.login })
        assertTrue(viewModel.state.value.repositories.isEmpty())
        assertFalse(viewModel.state.value.error)
    }

    private class ControlledSearchRepository : GithubRepositorySearchRepository {
        val requests = mutableListOf<PendingSearch>()

        // Allows old callbacks to arrive after cancellation, including an in-flight pagination request.
        override suspend fun search(query: String, page: Int, perPage: Int): Result<GithubPage<GithubRepository>> =
            suspendCoroutine { requests += PendingSearch(query, page, it) }
    }

    private class PendingSearch(
        val query: String,
        val page: Int,
        private val continuation: Continuation<Result<GithubPage<GithubRepository>>>
    ) {
        fun complete(items: List<GithubRepository>, nextPage: Int? = null) =
            continuation.resume(Result.success(GithubPage(items, nextPage)))
        fun fail() = continuation.resume(Result.failure(IllegalStateException("offline")))
    }

    private fun signedIn(id: Long, login: String) = GithubSession.SignedIn(
        GithubAccount(id, login, login, null, "https://avatars.example/$login", "https://github.com/$login", 1, 1, 1)
    )

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
