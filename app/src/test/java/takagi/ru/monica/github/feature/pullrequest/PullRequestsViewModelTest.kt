package takagi.ru.monica.github.feature.pullrequest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubMergeMethod
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubMergeResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestListQuery
import takagi.ru.monica.github.domain.GithubPullRequestRef
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubReviewEvent
import takagi.ru.monica.github.domain.GithubReviewState
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubReactionToggle
import takagi.ru.monica.github.domain.GithubRequestedReviewersUpdate
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubSortDirection
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class PullRequestsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listPaginatesAndResetsWhenClosedFilterIsSelected() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val viewModel = PullRequestsViewModel("openai", "codex", repository)
        advanceUntilIdle()
        viewModel.onAction(PullRequestsAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), viewModel.state.value.items.map { it.number })

        viewModel.onAction(PullRequestsAction.SelectState(GithubPullRequestState.CLOSED))
        advanceUntilIdle()

        assertEquals(GithubPullRequestState.CLOSED, viewModel.state.value.selectedState)
        assertTrue(viewModel.state.value.items.all { it.state == GithubPullRequestState.CLOSED })
    }

    @Test
    fun localSearchAndDraftFiltersDoNotReloadPullRequests() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val viewModel = PullRequestsViewModel("openai", "codex", repository)
        advanceUntilIdle()
        viewModel.onAction(PullRequestsAction.LoadMore)
        advanceUntilIdle()
        val requestsBeforeFiltering = repository.pullRequestQueries.size

        viewModel.onAction(PullRequestsAction.SelectDraftFilter(PullRequestDraftFilter.DRAFT))
        assertEquals(listOf(1), viewModel.state.value.visibleItems.map(GithubPullRequest::number))

        viewModel.onAction(PullRequestsAction.SelectDraftFilter(PullRequestDraftFilter.READY))
        assertEquals(listOf(2), viewModel.state.value.visibleItems.map(GithubPullRequest::number))

        viewModel.onAction(PullRequestsAction.SelectDraftFilter(PullRequestDraftFilter.ALL))
        viewModel.onAction(PullRequestsAction.SearchChanged("#2"))
        assertEquals(listOf(2), viewModel.state.value.visibleItems.map(GithubPullRequest::number))
        assertEquals(requestsBeforeFiltering, repository.pullRequestQueries.size)
    }

    @Test
    fun selectingPullRequestOrderingResetsPaginationAndSendsOneCombinedQuery() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val viewModel = PullRequestsViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(
            PullRequestsAction.SelectOrdering(
                sort = GithubListSort.CREATED,
                direction = GithubSortDirection.ASC
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.pullRequestPages)
        assertEquals(GithubListSort.CREATED, repository.pullRequestQueries.last().sort)
        assertEquals(GithubSortDirection.ASC, repository.pullRequestQueries.last().direction)
    }

    @Test
    fun detailLoadsConversationDiffAndReviewsThenSupportsWrites() = runTest(dispatcher) {
        val pullRequests = FakePullRequestsRepository()
        val issues = FakeIssuesRepository()
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, pullRequests, issues, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        assertEquals("app/Main.kt", viewModel.state.value.files.single().filename)
        assertEquals(GithubReviewState.APPROVED, viewModel.state.value.reviews.single().state)
        assertEquals("app/Main.kt", viewModel.state.value.reviewComments.single().path)
        assertEquals(42, viewModel.state.value.reviewComments.single().line)
        assertEquals("Conversation", viewModel.state.value.comments.single().body)

        viewModel.onAction(PullRequestDetailAction.ReviewBodyChanged("Ship it"))
        viewModel.onAction(PullRequestDetailAction.SubmitReview(GithubReviewEvent.APPROVE))
        viewModel.onAction(PullRequestDetailAction.ReviewBodyChanged("Edit while submitting"))
        assertEquals("Ship it", viewModel.state.value.reviewBody)
        advanceUntilIdle()
        assertEquals("Ship it", pullRequests.submittedReview?.body)
        assertEquals("", viewModel.state.value.reviewBody)
        viewModel.onAction(
            PullRequestDetailAction.Merge(
                method = GithubMergeMethod.SQUASH,
                commitTitle = "Native client (#1)",
                commitMessage = "Ship it"
            )
        )
        advanceUntilIdle()

        assertEquals(GithubReviewEvent.APPROVE, pullRequests.submittedReview?.event)
        assertEquals("head-sha", pullRequests.mergeDraft?.expectedHeadSha)
        assertEquals("Native client (#1)", pullRequests.mergeDraft?.commitTitle)
        assertTrue(viewModel.state.value.pullRequest?.isMerged == true)
        assertFalse(viewModel.state.value.isMerging)
    }

    @Test
    fun signedInViewerCanReactToPullRequestConversation() = runTest(dispatcher) {
        val issues = FakeIssuesRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            FakePullRequestsRepository(),
            issues,
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(
            PullRequestDetailAction.ToggleCommentReaction(901, GithubReactionContent.ROCKET)
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.ROCKET))
        assertTrue(GithubReactionContent.ROCKET in viewModel.state.value.activeReactions.getValue(901))
        assertEquals("joyins", issues.reactionViewers.single())
        assertFalse(901 in viewModel.state.value.reactionBusyCommentIds)
    }

    @Test
    fun mergeRejectsOversizedCommitTitleBeforeCallingRepository() = runTest(dispatcher) {
        val pullRequests = FakePullRequestsRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            pullRequests,
            FakeIssuesRepository(),
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(
            PullRequestDetailAction.Merge(
                method = GithubMergeMethod.SQUASH,
                commitTitle = "x".repeat(GithubMergeDraft.MAX_TITLE_LENGTH + 1),
                commitMessage = ""
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.mergeValidationError)
        assertTrue(pullRequests.mergeDraft == null)
        assertFalse(viewModel.state.value.isMerging)
    }

    @Test
    fun detailUpdatesTitleAndBodyAndReportsValidationErrors() = runTest(dispatcher) {
        val pullRequests = FakePullRequestsRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            pullRequests,
            FakeIssuesRepository(),
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(PullRequestDetailAction.UpdateContent("", "Body"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.contentValidationError)
        assertTrue(pullRequests.updatedDraft == null)

        viewModel.onAction(PullRequestDetailAction.UpdateContent("Updated PR", "Updated body"))
        advanceUntilIdle()

        assertEquals("Updated PR", pullRequests.updatedDraft?.title)
        assertEquals("Updated body", pullRequests.updatedDraft?.body)
        assertEquals("Updated PR", viewModel.state.value.pullRequest?.title)
        assertFalse(viewModel.state.value.isUpdatingContent)
        assertFalse(viewModel.state.value.contentUpdateError)
    }

    @Test
    fun failedPullRequestReactionKeepsCountAndMarksOnlyThatComment() = runTest(dispatcher) {
        val issues = FakeIssuesRepository().apply {
            reactionResult = Result.failure(IllegalStateException("network"))
        }
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            FakePullRequestsRepository(),
            issues,
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(
            PullRequestDetailAction.ToggleCommentReaction(901, GithubReactionContent.HEART)
        )
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.HEART))
        assertEquals(setOf(901L), viewModel.state.value.reactionErrorCommentIds)
        assertTrue(viewModel.state.value.activeReactions.isEmpty())
    }

    @Test
    fun detailLocksConversationAndRejectsNewCommentsWhileLocked() = runTest(dispatcher) {
        val issues = FakeIssuesRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            FakePullRequestsRepository(),
            issues,
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(PullRequestDetailAction.ToggleLock)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.pullRequest?.isLocked == true)
        assertFalse(viewModel.state.value.isUpdatingLock)
        assertFalse(viewModel.state.value.lockUpdateError)

        viewModel.onAction(PullRequestDetailAction.CommentChanged("Should not send"))
        viewModel.onAction(PullRequestDetailAction.SubmitComment)
        advanceUntilIdle()

        assertEquals(0, issues.addedComments)
    }

    @Test
    fun detailLoadsAndUpdatesPullRequestMetadataThroughIssuesApi() = runTest(dispatcher) {
        val issues = FakeIssuesRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            FakePullRequestsRepository(),
            issues,
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(PullRequestDetailAction.LoadLabels)
        viewModel.onAction(PullRequestDetailAction.LoadAssignees)
        viewModel.onAction(PullRequestDetailAction.LoadMilestones)
        advanceUntilIdle()

        assertEquals("bug", viewModel.state.value.availableLabels.single().name)
        assertEquals("bob", viewModel.state.value.availableAssignees.single().login)
        assertEquals(5, viewModel.state.value.availableMilestones.single().number)

        viewModel.onAction(PullRequestDetailAction.UpdateLabels(listOf("bug")))
        viewModel.onAction(PullRequestDetailAction.UpdateAssignees(listOf("bob")))
        viewModel.onAction(PullRequestDetailAction.UpdateMilestone(5))
        advanceUntilIdle()

        assertEquals(listOf("bug"), viewModel.state.value.pullRequest?.labels?.map { it.name })
        assertEquals(listOf("bob"), viewModel.state.value.pullRequest?.assignees?.map { it.login })
        assertEquals(5, viewModel.state.value.pullRequest?.milestone?.number)
        assertFalse(viewModel.state.value.isUpdatingLabels)
        assertFalse(viewModel.state.value.isUpdatingAssignees)
        assertFalse(viewModel.state.value.isUpdatingMilestone)
    }

    @Test
    fun detailUpdatesRequestedReviewersWithDedicatedPullRequestApi() = runTest(dispatcher) {
        val pullRequests = FakePullRequestsRepository()
        val viewModel = PullRequestDetailViewModel(
            "openai",
            "codex",
            1,
            pullRequests,
            FakeIssuesRepository(),
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(PullRequestDetailAction.UpdateRequestedReviewers(listOf("alice")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reviewersValidationError)
        assertTrue(pullRequests.requestedReviewersUpdate == null)

        viewModel.onAction(PullRequestDetailAction.UpdateRequestedReviewers(listOf("bob")))
        advanceUntilIdle()

        assertEquals(setOf("bob"), pullRequests.requestedReviewersUpdate?.requested)
        assertEquals(listOf("bob"), viewModel.state.value.pullRequest?.requestedReviewers?.map { it.login })
        assertFalse(viewModel.state.value.isUpdatingReviewers)
        assertFalse(viewModel.state.value.reviewersUpdateError)
    }

    @Test
    fun accountSwitchRevokesPullRequestManagementUntilTheNewRoleIsLoaded() = runTest(dispatcher) {
        val pullRequests = FakePullRequestsRepository()
        val details = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.ADMIN)
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, pullRequests, FakeIssuesRepository(), details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canMerge)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canMerge)
        assertFalse(viewModel.state.value.canRequestReviewers)
        assertFalse(viewModel.state.value.canLock)
        viewModel.onAction(PullRequestDetailAction.Merge(GithubMergeMethod.SQUASH))
        advanceUntilIdle()

        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertEquals(null, pullRequests.mergeDraft)
        assertFalse(viewModel.state.value.pullRequest?.isMerged == true)
    }

    @Test
    fun latePullRequestRoleResponseCannotGrantThePreviousAccountsPermissions() = runTest(dispatcher) {
        val oldRole = PendingResult<GithubRepositoryDetails>()
        val fallback = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.READ)
        var requests = 0
        val details = object : GithubRepositoryDetailsRepository by fallback {
            override suspend fun details(owner: String, name: String) =
                if (++requests == 1) oldRole.await() else fallback.details(owner, name)
        }
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, FakePullRequestsRepository(), FakeIssuesRepository(), details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        runCurrent()
        viewModel.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)

        oldRole.complete(Result.success(TestGithubRepositoryDetailsRepository.details(viewerRole = GithubCollaboratorRole.ADMIN)))
        advanceUntilIdle()

        assertEquals("reader", viewModel.state.value.viewerLogin)
        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canMerge)
    }

    @Test
    fun oldPullRequestCommentSuccessDoesNotClearTheNextAccountsPendingDraft() =
        verifyOldPullRequestCommentIgnored(failure = false)

    @Test
    fun oldPullRequestCommentFailureDoesNotFailTheNextAccountsPendingDraft() =
        verifyOldPullRequestCommentIgnored(failure = true)

    @Test
    fun sentPullRequestCommentDoesNotEraseTextTypedWhileTheRequestWasPending() = runTest(dispatcher) {
        val pending = PendingResult<GithubIssueComment>()
        val issues = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun addComment(owner: String, name: String, number: Int, draft: GithubIssueCommentDraft) =
                pending.await()
        }
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, FakePullRequestsRepository(), issues, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.CommentChanged("Sent comment"))
        viewModel.onAction(PullRequestDetailAction.SubmitComment)
        runCurrent()
        viewModel.onAction(PullRequestDetailAction.CommentChanged("Next draft"))
        val sent = viewModel.state.value.comments.single().copy(id = 999, body = "Sent comment")
        pending.complete(Result.success(sent))
        advanceUntilIdle()

        assertEquals("Next draft", viewModel.state.value.commentDraft)
        assertEquals(sent, viewModel.state.value.comments.last())
        assertFalse(viewModel.state.value.isSubmittingComment)
    }

    @Test
    fun previousAccountsReviewAndMergeDoNotChangeTheCurrentPullRequest() = runTest(dispatcher) {
        val oldReview = PendingResult<GithubPullRequestReview>()
        val oldMerge = PendingResult<GithubMergeResult>()
        val pullRequests = object : GithubPullRequestsRepository by FakePullRequestsRepository() {
            override suspend fun submitReview(owner: String, name: String, number: Int, draft: GithubPullRequestReviewDraft) =
                oldReview.await()
            override suspend fun merge(owner: String, name: String, number: Int, draft: GithubMergeDraft) =
                oldMerge.await()
        }
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, pullRequests, FakeIssuesRepository(), details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val reviewTemplate = viewModel.state.value.reviews.single()
        viewModel.onAction(PullRequestDetailAction.ReviewBodyChanged("Old review"))
        viewModel.onAction(PullRequestDetailAction.SubmitReview(GithubReviewEvent.APPROVE))
        viewModel.onAction(PullRequestDetailAction.Merge(GithubMergeMethod.SQUASH))
        runCurrent()
        assertTrue(viewModel.state.value.isSubmittingReview)
        assertTrue(viewModel.state.value.isMerging)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        assertEquals("Old review", viewModel.state.value.reviewBody)
        viewModel.onAction(PullRequestDetailAction.ReviewBodyChanged("Current review draft"))
        advanceUntilIdle()
        val current = viewModel.state.value
        assertFalse(current.isSubmittingReview)
        assertFalse(current.isMerging)
        oldReview.complete(Result.success(reviewTemplate.copy(id = 999, body = "Old review")))
        oldMerge.complete(Result.success(GithubMergeResult("old-merge", true, "Merged")))
        advanceUntilIdle()

        assertEquals(current, viewModel.state.value)
        assertEquals("Current review draft", viewModel.state.value.reviewBody)
        assertFalse(viewModel.state.value.pullRequest?.isMerged == true)
    }

    private fun verifyOldPullRequestCommentIgnored(failure: Boolean) = runTest(dispatcher) {
        val oldComment = PendingResult<GithubIssueComment>()
        val newComment = PendingResult<GithubIssueComment>()
        var requests = 0
        val issues = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun addComment(owner: String, name: String, number: Int, draft: GithubIssueCommentDraft) =
                if (++requests == 1) oldComment.await() else newComment.await()
        }
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = PullRequestDetailViewModel("openai", "codex", 1, FakePullRequestsRepository(), issues, details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val commentTemplate = viewModel.state.value.comments.single()
        viewModel.onAction(PullRequestDetailAction.CommentChanged("Old account draft"))
        viewModel.onAction(PullRequestDetailAction.SubmitComment)
        runCurrent()
        assertTrue(viewModel.state.value.isSubmittingComment)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        assertEquals("Old account draft", viewModel.state.value.commentDraft)
        assertFalse(viewModel.state.value.isSubmittingComment)
        viewModel.onAction(PullRequestDetailAction.CommentChanged("New account draft"))
        viewModel.onAction(PullRequestDetailAction.SubmitComment)
        runCurrent()
        val current = viewModel.state.value
        assertTrue(current.isSubmittingComment)
        assertEquals(2, requests)

        oldComment.complete(
            if (failure) Result.failure(IllegalStateException("old request failed"))
            else Result.success(commentTemplate.copy(id = 998, body = "Old account draft", author = user("joyins")))
        )
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value)

        val submitted = commentTemplate.copy(id = 999, body = "New account draft", author = user("reader"))
        newComment.complete(Result.success(submitted))
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.commentDraft)
        assertEquals(listOf(commentTemplate, submitted), viewModel.state.value.comments)
        assertFalse(viewModel.state.value.isSubmittingComment)
        assertFalse(viewModel.state.value.commentSubmitError)
    }

    private class PendingResult<T> {
        private var continuation: Continuation<Result<T>>? = null

        suspend fun await(): Result<T> = suspendCoroutine { pending ->
            check(continuation == null)
            continuation = pending
        }

        fun complete(result: Result<T>) {
            checkNotNull(continuation).resume(result)
            continuation = null
        }
    }

    private class FakePullRequestsRepository : GithubPullRequestsRepository {
        val pullRequestPages = mutableListOf<Int>()
        val pullRequestQueries = mutableListOf<GithubPullRequestListQuery>()
        var submittedReview: GithubPullRequestReviewDraft? = null
        var updatedDraft: GithubPullRequestDraft? = null
        var requestedReviewersUpdate: GithubRequestedReviewersUpdate? = null
        var mergeDraft: GithubMergeDraft? = null

        override suspend fun pullRequests(
            owner: String,
            name: String,
            query: GithubPullRequestListQuery,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubPullRequest>> {
            pullRequestPages += page
            pullRequestQueries += query
            return Result.success(
                GithubPage(
                    items = listOf(
                        pullRequest(
                            if (query.state == GithubPullRequestState.CLOSED) 9 else page,
                            query.state
                        ).copy(isDraft = query.state == GithubPullRequestState.OPEN && page == 1)
                    ),
                    nextPage = if (query.state == GithubPullRequestState.OPEN && page == 1) 2 else null
                )
            )
        }

        override suspend fun pullRequest(owner: String, name: String, number: Int) =
            Result.success(pullRequest(number, GithubPullRequestState.OPEN))

        override suspend fun files(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubPullRequestFile(
                            sha = "file-sha",
                            filename = "app/Main.kt",
                            status = "modified",
                            additions = 1,
                            deletions = 1,
                            changes = 2,
                            patch = "+new",
                            blobUrl = "https://github.com/openai/codex/blob/head/app/Main.kt",
                            rawUrl = null
                        )
                    ),
                    null
                )
            )

        override suspend fun reviews(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubPullRequestReview(
                            id = 801,
                            body = "Ship it",
                            state = GithubReviewState.APPROVED,
                            author = user("reviewer"),
                            submittedAt = "2026-08-16T01:00:00Z",
                            htmlUrl = "https://github.com/openai/codex/pull/$number#review"
                        )
                    ),
                    null
                )
            )

        override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubPullRequestReviewComment(
                            id = 901,
                            body = "Keep this branch explicit.",
                            path = "app/Main.kt",
                            line = 42,
                            startLine = 40,
                            side = "RIGHT",
                            diffHunk = "@@ -40 +40 @@",
                            author = user("reviewer"),
                            createdAt = "2026-08-16T01:30:00Z",
                            updatedAt = "2026-08-16T01:30:00Z",
                            htmlUrl = "https://github.com/openai/codex/pull/$number#discussion_r901"
                        )
                    ),
                    null
                )
            )

        override suspend fun submitReview(
            owner: String,
            name: String,
            number: Int,
            draft: GithubPullRequestReviewDraft
        ): Result<GithubPullRequestReview> {
            submittedReview = draft
            return Result.success(
                GithubPullRequestReview(
                    id = 802,
                    body = draft.body.orEmpty(),
                    state = GithubReviewState.APPROVED,
                    author = user("joyins"),
                    submittedAt = "2026-08-16T02:00:00Z",
                    htmlUrl = "https://github.com/openai/codex/pull/$number#review-802"
                )
            )
        }

        override suspend fun merge(owner: String, name: String, number: Int, draft: GithubMergeDraft): Result<GithubMergeResult> {
            mergeDraft = draft
            return Result.success(GithubMergeResult("merge-sha", merged = true, message = "Merged"))
        }

        override suspend fun updateState(
            owner: String,
            name: String,
            number: Int,
            state: GithubPullRequestState
        ) = Result.success(pullRequest(number, state))

        override suspend fun updateContent(
            owner: String,
            name: String,
            number: Int,
            draft: GithubPullRequestDraft
        ): Result<GithubPullRequest> {
            updatedDraft = draft
            return Result.success(
                pullRequest(number, GithubPullRequestState.OPEN).copy(
                    title = draft.title,
                    body = draft.body
                )
            )
        }

        override suspend fun updateRequestedReviewers(
            owner: String,
            name: String,
            number: Int,
            update: GithubRequestedReviewersUpdate
        ): Result<GithubPullRequest> {
            requestedReviewersUpdate = update
            return Result.success(
                pullRequest(number, GithubPullRequestState.OPEN).copy(
                    requestedReviewers = update.requested.map(::user)
                )
            )
        }
    }

    private class FakeIssuesRepository : GithubIssuesRepository {
        val reactionViewers = mutableListOf<String>()
        var addedComments = 0
        var reactionResult: Result<GithubReactionToggle> = Result.success(
            GithubReactionToggle(GithubReactionContent.ROCKET, active = true, reactionId = 1L)
        )

        override suspend fun issues(
            owner: String,
            name: String,
            query: GithubIssueListQuery,
            page: Int,
            perPage: Int
        ) =
            Result.success(GithubPage<GithubIssue>(emptyList(), null))

        override suspend fun searchInRepository(
            owner: String,
            name: String,
            text: String,
            query: GithubIssueListQuery,
            page: Int,
            perPage: Int
        ) = Result.success(GithubPage<GithubIssue>(emptyList(), null))

        override suspend fun issue(owner: String, name: String, number: Int) =
            Result.failure<GithubIssue>(UnsupportedOperationException())

        override suspend fun comments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubIssueComment(
                            id = 901,
                            body = "Conversation",
                            author = user("alice"),
                            createdAt = "2026-08-16T00:00:00Z",
                            updatedAt = "2026-08-16T00:00:00Z",
                            htmlUrl = "https://github.com/openai/codex/pull/$number#comment"
                        )
                    ),
                    null
                )
            )

        override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft) =
            Result.failure<GithubIssue>(UnsupportedOperationException())

        override suspend fun updateIssue(
            owner: String,
            name: String,
            number: Int,
            draft: GithubIssueDraft
        ) = Result.failure<GithubIssue>(UnsupportedOperationException())

        override suspend fun addComment(owner: String, name: String, number: Int, draft: GithubIssueCommentDraft) =
            Result.success(
                GithubIssueComment(
                    id = 902,
                    body = draft.body,
                    author = user("joyins"),
                    createdAt = "2026-08-16T02:00:00Z",
                    updatedAt = "2026-08-16T02:00:00Z",
                    htmlUrl = "https://github.com/openai/codex/pull/$number#comment-902"
                )
            ).also { addedComments += 1 }

        override suspend fun updateIssueState(owner: String, name: String, number: Int, state: GithubIssueState) =
            Result.failure<GithubIssue>(UnsupportedOperationException())

        override suspend fun updateIssueLock(owner: String, name: String, number: Int, locked: Boolean) =
            Result.success(issue(number = number, locked = locked))

        override suspend fun labels(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(label("bug")), null))

        override suspend fun updateIssueLabels(owner: String, name: String, number: Int, labels: List<String>) =
            Result.success(issue(number = number, labels = labels.map(::label)))

        override suspend fun assignees(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(user("bob")), null))

        override suspend fun updateIssueAssignees(
            owner: String,
            name: String,
            number: Int,
            assignees: List<String>
        ) = Result.success(issue(number = number, assignees = assignees.map(::user)))

        override suspend fun milestones(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(milestone(5)), null))

        override suspend fun updateIssueMilestone(
            owner: String,
            name: String,
            number: Int,
            milestoneNumber: Int?
        ) = Result.success(issue(number = number, milestone = milestoneNumber?.let(::milestone)))

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
        fun pullRequest(number: Int, state: GithubPullRequestState) = GithubPullRequest(
            id = number.toLong(),
            number = number,
            title = "PR $number",
            body = "Summary",
            state = state,
            isDraft = false,
            isMerged = false,
            mergeable = true,
            mergeableState = "clean",
            author = user("alice"),
            labels = emptyList(),
            assignees = emptyList(),
            requestedReviewers = emptyList(),
            head = GithubPullRequestRef("alice:feature", "feature", "head-sha", "alice/codex"),
            base = GithubPullRequestRef("openai:main", "main", "base-sha", "openai/codex"),
            comments = 1,
            reviewComments = 1,
            commits = 2,
            additions = 10,
            deletions = 2,
            changedFiles = 1,
            createdAt = "2026-08-15T00:00:00Z",
            updatedAt = "2026-08-16T00:00:00Z",
            closedAt = null,
            mergedAt = null,
            htmlUrl = "https://github.com/openai/codex/pull/$number"
        )

        fun user(login: String) = GithubUserSummary(login, null, "https://github.com/$login")

        fun label(name: String) = GithubIssueLabel(name, "d73a4a", null)

        fun milestone(number: Int) = GithubIssueMilestone(
            number = number,
            title = "Milestone $number",
            description = null,
            openIssues = 2,
            closedIssues = 1,
            dueOn = null
        )

        fun issue(
            number: Int,
            locked: Boolean = false,
            labels: List<GithubIssueLabel> = emptyList(),
            assignees: List<GithubUserSummary> = emptyList(),
            milestone: GithubIssueMilestone? = null
        ) = GithubIssue(
            id = number.toLong(),
            number = number,
            title = "PR $number",
            body = "Summary",
            state = GithubIssueState.OPEN,
            author = user("alice"),
            labels = labels,
            assignees = assignees,
            comments = 1,
            isLocked = locked,
            createdAt = "2026-08-15T00:00:00Z",
            updatedAt = "2026-08-16T00:00:00Z",
            closedAt = null,
            htmlUrl = "https://github.com/openai/codex/pull/$number",
            milestone = milestone
        )

        fun signedInSession(login: String, id: Long = 1) = GithubSession.SignedIn(
            GithubAccount(
                id = id,
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
