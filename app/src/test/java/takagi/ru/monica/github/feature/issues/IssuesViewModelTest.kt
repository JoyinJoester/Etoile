package takagi.ru.monica.github.feature.issues

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
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubReactionToggle
import takagi.ru.monica.github.domain.GithubSortDirection

@OptIn(ExperimentalCoroutinesApi::class)
class IssuesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listLoadsNextPageWithoutDiscardingTheFirstPage() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), viewModel.state.value.items.map { it.number })
        assertEquals(listOf(1, 2), repository.issuePages)
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun selectingClosedFilterResetsItemsAndLoadsTheClosedEndpoint() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.SelectState(GithubIssueState.CLOSED))
        advanceUntilIdle()

        assertEquals(GithubIssueState.CLOSED, viewModel.state.value.selectedState)
        assertTrue(viewModel.state.value.items.all { it.state == GithubIssueState.CLOSED })
        assertEquals(GithubIssueState.CLOSED, repository.issueStates.last())
    }

    @Test
    fun searchFiltersOnlyLoadedIssuesWithoutStartingAnotherRequest() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()
        viewModel.onAction(IssuesAction.LoadMore)
        advanceUntilIdle()
        val requestsBeforeSearch = repository.issueQueries.size

        viewModel.onAction(IssuesAction.SearchChanged("#2"))

        assertEquals(listOf(1, 2), viewModel.state.value.items.map(GithubIssue::number))
        assertEquals(listOf(2), viewModel.state.value.visibleItems.map(GithubIssue::number))
        assertEquals(requestsBeforeSearch, repository.issueQueries.size)
    }

    @Test
    fun selectingOrderingResetsPaginationAndSendsOneCombinedQuery() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(
            IssuesAction.SelectOrdering(
                sort = GithubListSort.CREATED,
                direction = GithubSortDirection.ASC
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.issuePages)
        assertEquals(GithubListSort.CREATED, repository.issueQueries.last().sort)
        assertEquals(GithubSortDirection.ASC, repository.issueQueries.last().direction)
        assertEquals(1, viewModel.state.value.items.single().number)
    }

    @Test
    fun detailLoadsIssueAndCommentsIndependently() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.issue?.number)
        assertEquals("First comment", viewModel.state.value.comments.single().body)
        assertFalse(viewModel.state.value.isLoadingIssue)
        assertFalse(viewModel.state.value.isLoadingComments)
    }

    @Test
    fun createIssueValidatesAndPublishesTheCreatedIssue() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = CreateIssueViewModel("openai", "codex", repository)

        viewModel.onAction(CreateIssueAction.TitleChanged("New issue"))
        viewModel.onAction(CreateIssueAction.BodyChanged("Details"))
        viewModel.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()

        assertEquals("New issue", repository.createdDraft?.title)
        assertEquals(77, viewModel.state.value.createdIssue?.number)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun detailCanCommentAndCloseAnIssueWithoutReloadingTheThread() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()

        viewModel.onAction(IssueDetailAction.CommentChanged("A new comment"))
        viewModel.onAction(IssueDetailAction.SubmitComment)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.ToggleState)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.ToggleLock)
        advanceUntilIdle()

        assertEquals("A new comment", repository.createdComment?.body)
        assertEquals("A new comment", viewModel.state.value.comments.last().body)
        assertEquals(GithubIssueState.CLOSED, viewModel.state.value.issue?.state)
        assertTrue(viewModel.state.value.issue?.isLocked == true)
        assertFalse(viewModel.state.value.isUpdatingLock)
    }

    @Test
    fun signedInViewerCanToggleACommentReaction() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        viewModel.onAction(
            IssueDetailAction.ToggleCommentReaction(501, GithubReactionContent.HEART)
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.HEART))
        assertTrue(GithubReactionContent.HEART in viewModel.state.value.activeReactions.getValue(501))
        assertFalse(501 in viewModel.state.value.reactionBusyCommentIds)
        assertEquals("joyins", repository.reactionViewers.single())
    }

    @Test
    fun detailLoadsLabelsOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.labelsLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        viewModel.onAction(IssueDetailAction.LoadLabels)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateLabels(listOf("bug")))
        advanceUntilIdle()

        assertEquals(listOf("bug", "enhancement"), viewModel.state.value.availableLabels.map(GithubIssueLabel::name))
        assertEquals(listOf("bug"), repository.updatedLabels)
        assertEquals(listOf("bug"), viewModel.state.value.issue?.labels?.map(GithubIssueLabel::name))
        assertFalse(viewModel.state.value.isUpdatingLabels)
    }

    @Test
    fun detailLoadsAssigneesOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.assigneesLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        viewModel.onAction(IssueDetailAction.LoadAssignees)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateAssignees(listOf("alice")))
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob"), viewModel.state.value.availableAssignees.map { it.login })
        assertEquals(listOf("alice"), repository.updatedAssignees)
        assertEquals(listOf("alice"), viewModel.state.value.issue?.assignees?.map { it.login })
        assertFalse(viewModel.state.value.isUpdatingAssignees)
    }

    @Test
    fun detailLoadsMilestonesOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.milestonesLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        viewModel.onAction(IssueDetailAction.LoadMilestones)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateMilestone(3))
        advanceUntilIdle()

        assertEquals(listOf("v1.0"), viewModel.state.value.availableMilestones.map(GithubIssueMilestone::title))
        assertEquals(3, repository.updatedMilestone)
        assertEquals(3, viewModel.state.value.issue?.milestone?.number)
        assertFalse(viewModel.state.value.isUpdatingMilestone)
    }

    @Test
    fun detailUpdatesContentAfterDraftValidation() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))

        viewModel.onAction(IssueDetailAction.UpdateContent("Updated title", "Updated body"))
        advanceUntilIdle()

        assertEquals("Updated title", viewModel.state.value.issue?.title)
        assertEquals("Updated body", viewModel.state.value.issue?.body)
        assertFalse(viewModel.state.value.contentValidationError)
        assertFalse(viewModel.state.value.contentUpdateError)
    }

    @Test
    fun failedCommentReactionKeepsCountsAndExposesLocalError() = runTest(dispatcher) {
        val repository = FakeIssuesRepository().apply {
            reactionResult = Result.failure(IllegalStateException("network"))
        }
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        viewModel.onAction(
            IssueDetailAction.ToggleCommentReaction(501, GithubReactionContent.ROCKET)
        )
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.ROCKET))
        assertTrue(501 in viewModel.state.value.reactionErrorCommentIds)
        assertFalse(501 in viewModel.state.value.reactionBusyCommentIds)
    }

    private class FakeIssuesRepository : GithubIssuesRepository {
        val issuePages = mutableListOf<Int>()
        val issueStates = mutableListOf<GithubIssueState>()
        val issueQueries = mutableListOf<GithubIssueListQuery>()
        var createdDraft: GithubIssueDraft? = null
        var createdComment: GithubIssueCommentDraft? = null
        var currentDetailState: GithubIssueState = GithubIssueState.OPEN
        var updatedLabels: List<String> = emptyList()
        var updatedAssignees: List<String> = emptyList()
        var updatedMilestone: Int? = null
        val reactionViewers = mutableListOf<String>()
        var reactionResult: Result<GithubReactionToggle> = Result.success(
            GithubReactionToggle(GithubReactionContent.HEART, active = true, reactionId = 1L)
        )

        override suspend fun issues(
            owner: String,
            name: String,
            query: GithubIssueListQuery,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubIssue>> {
            val state = query.state
            issuePages += page
            issueStates += state
            issueQueries += query
            val item = issue(
                number = if (state == GithubIssueState.CLOSED) 9 else page,
                state = state
            )
            return Result.success(GithubPage(listOf(item), nextPage = if (state == GithubIssueState.OPEN && page == 1) 2 else null))
        }

        override suspend fun issue(owner: String, name: String, number: Int) =
            Result.success(issue(number, GithubIssueState.OPEN))

        override suspend fun comments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    items = listOf(
                        GithubIssueComment(
                            id = 501,
                            body = "First comment",
                            author = user("maintainer"),
                            createdAt = "2026-08-16T01:00:00Z",
                            updatedAt = "2026-08-16T01:00:00Z",
                            htmlUrl = "https://github.com/openai/codex/issues/$number#issuecomment-501"
                        )
                    ),
                    nextPage = null
                )
            )

        override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft): Result<GithubIssue> {
            createdDraft = draft
            return Result.success(issue(77, GithubIssueState.OPEN))
        }

        override suspend fun updateIssue(
            owner: String,
            name: String,
            number: Int,
            draft: GithubIssueDraft
        ) = Result.success(issue(number, currentDetailState).copy(title = draft.title, body = draft.body))

        override suspend fun addComment(
            owner: String,
            name: String,
            number: Int,
            draft: GithubIssueCommentDraft
        ): Result<GithubIssueComment> {
            createdComment = draft
            return Result.success(
                GithubIssueComment(
                    id = 999,
                    body = draft.body,
                    author = user("joyins"),
                    createdAt = "2026-08-16T02:00:00Z",
                    updatedAt = "2026-08-16T02:00:00Z",
                    htmlUrl = "https://github.com/openai/codex/issues/$number#issuecomment-999"
                )
            )
        }

        override suspend fun updateIssueState(
            owner: String,
            name: String,
            number: Int,
            state: GithubIssueState
        ): Result<GithubIssue> {
            currentDetailState = state
            return Result.success(issue(number, state))
        }

        override suspend fun updateIssueLock(
            owner: String,
            name: String,
            number: Int,
            locked: Boolean
        ) = Result.success(issue(number, currentDetailState).copy(isLocked = locked))

        override suspend fun labels(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubIssueLabel("bug", "d73a4a", null),
                        GithubIssueLabel("enhancement", "a2eeef", null)
                    ),
                    null
                )
            )

        override suspend fun updateIssueLabels(
            owner: String,
            name: String,
            number: Int,
            labels: List<String>
        ): Result<GithubIssue> {
            updatedLabels = labels
            return Result.success(
                issue(number, currentDetailState).copy(
                    labels = labels.map { GithubIssueLabel(it, "d73a4a", null) }
                )
            )
        }

        override suspend fun assignees(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(user("alice"), user("bob")), null))

        override suspend fun updateIssueAssignees(
            owner: String,
            name: String,
            number: Int,
            assignees: List<String>
        ): Result<GithubIssue> {
            updatedAssignees = assignees
            return Result.success(
                issue(number, currentDetailState).copy(assignees = assignees.map(::user))
            )
        }

        override suspend fun milestones(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(milestone()), null))

        override suspend fun updateIssueMilestone(
            owner: String,
            name: String,
            number: Int,
            milestoneNumber: Int?
        ): Result<GithubIssue> {
            updatedMilestone = milestoneNumber
            return Result.success(
                issue(number, currentDetailState).copy(
                    milestone = milestoneNumber?.let { milestone() }
                )
            )
        }

        override suspend fun toggleCommentReaction(
            owner: String,
            name: String,
            commentId: Long,
            content: GithubReactionContent,
            viewerLogin: String
        ): Result<GithubReactionToggle> {
            reactionViewers += viewerLogin
            return reactionResult.map { it.copy(content = content) }
        }
    }

    private companion object {
        fun issue(number: Int, state: GithubIssueState) = GithubIssue(
            id = number.toLong(),
            number = number,
            title = "Issue $number",
            body = "Body",
            state = state,
            author = user("alice"),
            labels = emptyList(),
            assignees = emptyList(),
            comments = 1,
            isLocked = false,
            createdAt = "2026-08-15T00:00:00Z",
            updatedAt = "2026-08-16T00:00:00Z",
            closedAt = if (state == GithubIssueState.CLOSED) "2026-08-16T00:00:00Z" else null,
            htmlUrl = "https://github.com/openai/codex/issues/$number"
        )

        fun user(login: String) = GithubUserSummary(
            login = login,
            avatarUrl = null,
            htmlUrl = "https://github.com/$login"
        )

        fun milestone() = GithubIssueMilestone(
            number = 3,
            title = "v1.0",
            description = "Launch",
            openIssues = 4,
            closedIssues = 6,
            dueOn = "2026-09-01T00:00:00Z"
        )

        fun signedInSession(login: String) = GithubSession.SignedIn(
            GithubAccount(
                id = 1,
                login = login,
                name = null,
                bio = null,
                avatarUrl = "https://avatars.githubusercontent.com/u/1",
                htmlUrl = "https://github.com/$login",
                publicRepositories = 0,
                followers = 0,
                following = 0
            )
        )
    }
}
