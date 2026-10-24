package takagi.ru.monica.github.feature.pullrequest

import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
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
import takagi.ru.monica.github.domain.GithubReviewCommentReplyDraft
import takagi.ru.monica.github.domain.GithubReviewLineComment
import takagi.ru.monica.github.domain.GithubReviewSubmissionDraft
import takagi.ru.monica.github.domain.GithubDiffSide
import takagi.ru.monica.github.domain.GithubReviewThreadStatus
import takagi.ru.monica.github.domain.GithubReviewThreadPage
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
    @Test fun switchingAccountHidesPreviousPullRequestAndScopesTextDrafts() = runTest(dispatcher) {
        val vm = replyViewModel(FakePullRequestsRepository())
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        vm.onAction(PullRequestDetailAction.CommentChanged("Private account A comment"))
        vm.onAction(PullRequestDetailAction.ReviewBodyChanged("Private account A review"))
        assertTrue(vm.state.value.pullRequest != null)
        vm.onSessionChanged(signedInSession("reader", 2))
        assertNull(vm.state.value.pullRequest)
        assertTrue(vm.state.value.files.isEmpty())
        assertTrue(vm.state.value.reviews.isEmpty())
        assertTrue(vm.state.value.comments.isEmpty())
        assertEquals("", vm.state.value.commentDraft)
        assertEquals("", vm.state.value.reviewBody)
        advanceUntilIdle()
        vm.onSessionChanged(signedInSession("reviewer"))
        assertEquals("Private account A comment", vm.state.value.commentDraft)
        assertEquals("Private account A review", vm.state.value.reviewBody)
        advanceUntilIdle()
    }

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
        assertEquals("", viewModel.state.value.reviewBody)
        assertNull(viewModel.state.value.pullRequest)
        assertTrue(viewModel.state.value.reviews.isEmpty())
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
        assertEquals("", viewModel.state.value.commentDraft)
        assertNull(viewModel.state.value.pullRequest)
        assertTrue(viewModel.state.value.comments.isEmpty())
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

    @Test
    fun reviewReplyUsesRootThreadAndOnlyServerResponseIsAdded() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val root = repository.reviewComments("openai", "codex", 1, 1, 30).getOrThrow().items.single()
        val existingReply = root.copy(id = 902, inReplyToId = root.id)
        val serverReply = root.copy(id = 903, inReplyToId = root.id, body = "Server reply", author = user("joyins"))
        repository.replyResponse = { Result.success(serverReply) }
        val threads = object : GithubPullRequestsRepository by repository {
            override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
                Result.success(GithubPage(listOf(existingReply), null))
        }
        val saved = SavedStateHandle()
        val viewModel = replyViewModel(threads, saved)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val conversation = viewModel.state.value.comments
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(existingReply.id))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("  Sent reply  "))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()

        assertEquals(root.id, repository.replyRequests.single().threadId)
        assertEquals("Sent reply", repository.replyRequests.single().body)
        assertEquals(listOf(existingReply, serverReply), viewModel.state.value.reviewComments)
        assertEquals(conversation, viewModel.state.value.comments)
        assertTrue(viewModel.state.value.reviewReply.sent)
        assertFalse(viewModel.state.value.reviewReply.isEditorOpen)
        assertEquals("", viewModel.state.value.reviewReply.body)
        assertTrue(saved.keys().isEmpty())
        assertFalse(viewModel.state.value.reviewThreads.single().hasRoot)
    }

    @Test
    fun reviewReplyFailureRetainsDraftAndRequiresExplicitRetry() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        repository.replyResponse = { Result.failure(IllegalStateException("denied")) }
        val viewModel = replyViewModel(repository)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        assertTrue(viewModel.state.value.reviewReply.validationError)
        assertTrue(repository.replyRequests.isEmpty())
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Draft"))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reviewReply.submitError)
        assertEquals("Draft", viewModel.state.value.reviewReply.body)
        assertEquals(901L, viewModel.state.value.reviewReply.threadId)
        assertTrue(viewModel.state.value.reviewReply.isEditorOpen)
        viewModel.onAction(PullRequestDetailAction.CloseReviewReply)
        viewModel.onAction(PullRequestDetailAction.ResumeReviewReply)
        advanceUntilIdle()
        assertEquals(1, repository.replyRequests.size)
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()
        assertEquals(2, repository.replyRequests.size)
    }

    @Test
    fun reviewReplyIsSingleFlightAndSubmissionPreventsEditingOrClosing() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubPullRequestReviewComment>()
        repository.replyResponse = { pending.await() }
        val viewModel = replyViewModel(repository)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val root = viewModel.state.value.reviewComments.single()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(root.id))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Draft"))
        repeat(2) { viewModel.onAction(PullRequestDetailAction.SubmitReviewReply) }
        runCurrent()
        viewModel.onAction(PullRequestDetailAction.CloseReviewReply)
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Replacement"))
        assertEquals(1, repository.replyRequests.size)
        assertEquals("Draft", viewModel.state.value.reviewReply.body)
        assertTrue(viewModel.state.value.reviewReply.isEditorOpen)
        pending.complete(Result.success(root.copy(id = 902, inReplyToId = root.id)))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.reviewReply.isSubmitting)
    }

    @Test
    fun reviewReplyRejectsSignedOutUnknownTargetsAndLockedPullRequest() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val viewModel = replyViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        assertFalse(viewModel.state.value.reviewReply.isEditorOpen)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(999))
        assertFalse(viewModel.state.value.reviewReply.isEditorOpen)
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Draft before lock"))
        viewModel.onAction(PullRequestDetailAction.ToggleLock)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canReplyToReview)
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()
        assertTrue(repository.replyRequests.isEmpty())
        assertEquals("Draft before lock", viewModel.state.value.reviewReply.body)
    }

    @Test
    fun changingReviewThreadRequiresDiscardConfirmationAndCancelKeepsDraft() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val root = repository.reviewComments("openai", "codex", 1, 1, 30).getOrThrow().items.single()
        val threads = object : GithubPullRequestsRepository by repository {
            override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
                Result.success(GithubPage(listOf(root, root.copy(id = 910)), null))
        }
        val viewModel = replyViewModel(threads)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Keep me"))
        viewModel.onAction(PullRequestDetailAction.CloseReviewReply)
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(910))
        assertEquals(910L, viewModel.state.value.reviewReply.pendingThreadId)
        assertEquals(901L, viewModel.state.value.reviewReply.threadId)
        viewModel.onAction(PullRequestDetailAction.CancelReplaceReviewReply)
        assertEquals("Keep me", viewModel.state.value.reviewReply.body)
        assertEquals(null, viewModel.state.value.reviewReply.pendingThreadId)
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(910))
        viewModel.onAction(PullRequestDetailAction.ConfirmReplaceReviewReply)
        assertEquals(910L, viewModel.state.value.reviewReply.threadId)
        assertEquals("", viewModel.state.value.reviewReply.body)
        assertTrue(viewModel.state.value.reviewReply.isEditorOpen)
        assertTrue(repository.replyRequests.isEmpty())
    }

    @Test
    fun reviewReplyDraftsRestorePerAccountWithoutRestoringPendingPost() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubPullRequestReviewComment>()
        repository.replyResponse = { pending.await() }
        val saved = SavedStateHandle()
        val viewModel = replyViewModel(repository, saved)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("First account"))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        runCurrent()
        val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = replyViewModel(repository, snapshot)
        restored.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertEquals("First account", restored.state.value.reviewReply.body)
        assertTrue(restored.state.value.reviewReply.isEditorOpen)
        assertFalse(restored.state.value.reviewReply.isSubmitting)
        assertEquals(null, restored.state.value.reviewReply.pendingThreadId)
        assertEquals(1, repository.replyRequests.size)
        restored.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        assertEquals(null, restored.state.value.reviewReply.threadId)
        restored.onAction(PullRequestDetailAction.OpenReviewReply(901))
        restored.onAction(PullRequestDetailAction.ReviewReplyChanged("Second account"))
        restored.onAction(PullRequestDetailAction.CloseReviewReply)
        restored.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertEquals("First account", restored.state.value.reviewReply.body)
        restored.onSessionChanged(GithubSession.SignedOut)
        assertEquals(null, restored.state.value.reviewReply.threadId)
        restored.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        assertEquals("Second account", restored.state.value.reviewReply.body)
        assertFalse(restored.state.value.reviewReply.isEditorOpen)
        pending.complete(Result.failure(IllegalStateException("failed")))
        advanceUntilIdle()
    }

    @Test
    fun oldAccountsReviewReplySuccessCannotChangeNewAccountsDraft() = verifyOldReviewReplyIgnored(false)

    @Test
    fun oldAccountsReviewReplyFailureCannotChangeNewAccountsDraft() = verifyOldReviewReplyIgnored(true)

    @Test
    fun restoredReviewReplyWaitsForItsTargetAndDoesNotRestoreDiscardConfirmation() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val root = repository.reviewComments("openai", "codex", 1, 1, 30).getOrThrow().items.single()
        var visible = listOf(root, root.copy(id = 910))
        val threads = object : GithubPullRequestsRepository by repository {
            override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
                Result.success(GithubPage(visible, null))
        }
        val saved = SavedStateHandle()
        val viewModel = replyViewModel(threads, saved)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Unsent reply"))
        viewModel.onAction(PullRequestDetailAction.CloseReviewReply)
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(910))
        assertEquals(910L, viewModel.state.value.reviewReply.pendingThreadId)
        visible = emptyList()
        val restored = replyViewModel(threads, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        restored.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertEquals(null, restored.state.value.reviewReply.pendingThreadId)
        restored.onAction(PullRequestDetailAction.ResumeReviewReply)
        restored.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()
        assertTrue(repository.replyRequests.isEmpty())
        assertEquals("Unsent reply", restored.state.value.reviewReply.body)
        assertFalse(restored.state.value.hasReplyTarget)
        visible = listOf(root)
        restored.onAction(PullRequestDetailAction.RetryReviewComments)
        advanceUntilIdle()
        assertTrue(restored.state.value.hasReplyTarget)
        assertEquals("Unsent reply", restored.state.value.reviewReply.body)
        assertTrue(repository.replyRequests.isEmpty())
    }

    private fun verifyOldReviewReplyIgnored(failure: Boolean) = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubPullRequestReviewComment>()
        repository.replyResponse = { pending.await() }
        val viewModel = replyViewModel(repository)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val root = viewModel.state.value.reviewComments.single()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Old account"))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        runCurrent()
        viewModel.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Current account"))
        val current = viewModel.state.value
        pending.complete(if (failure) Result.failure(IllegalStateException("late failure"))
            else Result.success(root.copy(id = 902, inReplyToId = root.id)))
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertEquals("Old account", viewModel.state.value.reviewReply.body)
        assertFalse(viewModel.state.value.reviewReply.isSubmitting)
    }

    @Test
    fun lateReviewCommentsPageDoesNotOverwriteConfirmedReplyAndCanBeRetried() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val root = repository.reviewComments("openai", "codex", 1, 1, 30).getOrThrow().items.single()
        val pending = PendingResult<GithubPage<GithubPullRequestReviewComment>>()
        var pageTwoRequests = 0
        val threads = object : GithubPullRequestsRepository by repository {
            override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
                if (page == 1) Result.success(GithubPage(listOf(root), 2))
                else if (++pageTwoRequests == 1) pending.await()
                else Result.success(GithubPage(listOf(root.copy(id = 910)), null))
        }
        val sent = root.copy(id = 902, inReplyToId = root.id, body = "Confirmed")
        repository.replyResponse = { Result.success(sent) }
        val viewModel = replyViewModel(threads)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(PullRequestDetailAction.LoadMoreReviewComments)
        runCurrent()
        viewModel.onAction(PullRequestDetailAction.OpenReviewReply(901))
        viewModel.onAction(PullRequestDetailAction.ReviewReplyChanged("Reply"))
        viewModel.onAction(PullRequestDetailAction.SubmitReviewReply)
        advanceUntilIdle()
        val current = viewModel.state.value
        pending.complete(Result.success(GithubPage(listOf(root.copy(id = 911)), null)))
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value)
        viewModel.onAction(PullRequestDetailAction.LoadMoreReviewComments)
        advanceUntilIdle()
        assertEquals(listOf(901L, 902L, 910L), viewModel.state.value.reviewComments.map { it.id })
        assertEquals(2, pageTwoRequests)
    }

    @Test
    fun batchReviewQueuesBothDiffSidesAndPublishesOnceAfterConfirmation() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val saved = SavedStateHandle()
        val vm = replyViewModel(repository, saved)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.LEFT, "  old line  ")
        queueLine(vm, GithubDiffSide.RIGHT, "new line")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Body(" summary "))
        workspace(vm, PullRequestReviewWorkspaceAction.Event(GithubReviewEvent.REQUEST_CHANGES))
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        assertEquals(ReviewWorkspaceConfirmation.Submit, vm.state.value.reviewWorkspace.confirmation)
        assertTrue(repository.batchRequests.isEmpty())
        workspace(vm, PullRequestReviewWorkspaceAction.Cancel)
        assertEquals(2, vm.state.value.reviewWorkspace.comments.size)
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        repeat(2) { workspace(vm, PullRequestReviewWorkspaceAction.Confirm) }
        advanceUntilIdle()
        val draft = repository.batchRequests.single()
        assertEquals("head-sha", draft.commitSha)
        assertEquals(listOf(GithubDiffSide.LEFT, GithubDiffSide.RIGHT), draft.comments.map { it.side })
        assertEquals("old line", draft.comments.first().body)
        assertEquals("summary", draft.body)
        assertEquals(GithubReviewEvent.REQUEST_CHANGES, draft.event)
        assertTrue(repository.lineRequests.isEmpty())
        assertFalse(vm.state.value.reviewWorkspace.hasDraft)
        assertTrue(vm.state.value.reviewWorkspace.sent)
        assertTrue(saved.keys().isEmpty())
        assertEquals(990L, vm.state.value.reviews.first().id)
    }

    @Test
    fun standaloneDiffCommentRejectsUnknownCoordinatesAndUsesServerResponse() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val vm = replyViewModel(repository)
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.RIGHT))
        assertEquals(null, vm.state.value.reviewWorkspace.editor)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("unknown.kt", 1, GithubDiffSide.RIGHT))
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 999, GithubDiffSide.RIGHT))
        assertEquals(null, vm.state.value.reviewWorkspace.editor)
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.LEFT))
        workspace(vm, PullRequestReviewWorkspaceAction.LineBody("Delete line"))
        repeat(2) { workspace(vm, PullRequestReviewWorkspaceAction.SendLine) }
        advanceUntilIdle()
        assertEquals("head-sha", repository.lineRequests.single().first)
        assertEquals(GithubDiffSide.LEFT, repository.lineRequests.single().second.side)
        assertEquals("Delete line", vm.state.value.reviewComments.last().body)
        assertEquals(990L, vm.state.value.reviewComments.last().id)
        assertFalse(vm.state.value.reviewWorkspace.isLineOpen)
    }

    @Test
    fun batchFailureKeepsDraftAndBlocksChangesWhileSending() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubPullRequestReview>()
        repository.batchResponse = { pending.await() }
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.RIGHT, "Keep me")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        runCurrent()
        workspace(vm, PullRequestReviewWorkspaceAction.Close)
        workspace(vm, PullRequestReviewWorkspaceAction.Body("New summary"))
        workspace(vm, PullRequestReviewWorkspaceAction.Clear)
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        assertTrue(vm.state.value.reviewWorkspace.isOpen)
        assertEquals("", vm.state.value.reviewWorkspace.body)
        assertEquals(1, repository.batchRequests.size)
        pending.complete(Result.failure(IllegalStateException("failed")))
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.sendError)
        assertEquals("Keep me", vm.state.value.reviewWorkspace.comments.single().body)
        assertFalse(vm.state.value.reviewWorkspace.isSending)
        assertEquals(null, vm.state.value.reviewWorkspace.confirmation)
    }

    @Test
    fun freshPreflightRejectsHeadChangesClosedLockedAndFailedReadsWithoutPosting() = runTest(dispatcher) {
        val base = pullRequest(1, GithubPullRequestState.OPEN)
        val snapshots = listOf(Result.success(base.copy(head = base.head.copy(sha = "new-head"))),
            Result.success(base.copy(isLocked = true)), Result.success(base.copy(state = GithubPullRequestState.CLOSED)),
            Result.failure(IllegalStateException("offline")))
        for (snapshot in snapshots) {
            val repository = FakePullRequestsRepository()
            repository.snapshotResponse = { snapshot }
            val vm = replyViewModel(repository)
            vm.onSessionChanged(signedInSession("reviewer"))
            advanceUntilIdle()
            workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.RIGHT))
            workspace(vm, PullRequestReviewWorkspaceAction.LineBody("Keep me"))
            workspace(vm, PullRequestReviewWorkspaceAction.SendLine)
            advanceUntilIdle()
            assertTrue(repository.lineRequests.isEmpty())
            assertEquals("Keep me", vm.state.value.reviewWorkspace.editor?.body)
            assertTrue(vm.state.value.reviewWorkspace.stale || vm.state.value.reviewWorkspace.sendError)
        }
    }

    @Test
    fun batchPreflightRejectsMovedHeadAndOwnApproval() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val base = pullRequest(1, GithubPullRequestState.OPEN)
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.RIGHT, "Note")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        repository.snapshotResponse = { Result.success(base.copy(head = base.head.copy(sha = "moved"))) }
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        advanceUntilIdle()
        assertTrue(repository.batchRequests.isEmpty())
        assertEquals(1, vm.state.value.reviewWorkspace.comments.size)
        assertTrue(vm.state.value.reviewWorkspace.stale)
        vm.onSessionChanged(signedInSession(base.author.login, 2))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Event(GithubReviewEvent.APPROVE))
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        assertEquals(null, vm.state.value.reviewWorkspace.confirmation)
        assertTrue(vm.state.value.reviewWorkspace.validationError)
    }

    @Test
    fun draftEditingRemovalAndClearRequireConfirmationAndPreserveUnsentEdits() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.RIGHT, "Original")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Edit(0))
        workspace(vm, PullRequestReviewWorkspaceAction.LineBody("Edited"))
        workspace(vm, PullRequestReviewWorkspaceAction.QueueLine)
        assertEquals("Edited", vm.state.value.reviewWorkspace.comments.single().body)
        workspace(vm, PullRequestReviewWorkspaceAction.Remove(0))
        workspace(vm, PullRequestReviewWorkspaceAction.Cancel)
        assertEquals(1, vm.state.value.reviewWorkspace.comments.size)
        workspace(vm, PullRequestReviewWorkspaceAction.Remove(0))
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        assertTrue(vm.state.value.reviewWorkspace.comments.isEmpty())
        workspace(vm, PullRequestReviewWorkspaceAction.Body("Summary"))
        workspace(vm, PullRequestReviewWorkspaceAction.Clear)
        workspace(vm, PullRequestReviewWorkspaceAction.Cancel)
        assertEquals("Summary", vm.state.value.reviewWorkspace.body)
        workspace(vm, PullRequestReviewWorkspaceAction.Clear)
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        assertFalse(vm.state.value.reviewWorkspace.hasDraft)
        assertTrue(repository.batchRequests.isEmpty())
    }

    @Test
    fun batchDraftRestoresPerAccountWithoutRestoringPostOrConfirmation() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val saved = SavedStateHandle()
        val pending = PendingResult<GithubPullRequestReview>()
        repository.batchResponse = { pending.await() }
        val vm = replyViewModel(repository, saved)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.LEFT, "First account")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        val confirmSnapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        runCurrent()
        val postSnapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        for (snapshot in listOf(confirmSnapshot, postSnapshot)) {
            val restored = replyViewModel(repository, snapshot)
            restored.onSessionChanged(signedInSession("reviewer"))
            advanceUntilIdle()
            assertEquals("First account", restored.state.value.reviewWorkspace.comments.single().body)
            assertTrue(restored.state.value.reviewWorkspace.isOpen)
            assertFalse(restored.state.value.reviewWorkspace.isSending)
            assertEquals(null, restored.state.value.reviewWorkspace.confirmation)
            restored.onSessionChanged(signedInSession("other", 2))
            advanceUntilIdle()
            assertFalse(restored.state.value.reviewWorkspace.hasDraft)
            restored.onSessionChanged(signedInSession("reviewer"))
            advanceUntilIdle()
            assertEquals("First account", restored.state.value.reviewWorkspace.comments.single().body)
        }
        assertEquals(1, repository.batchRequests.size)
        pending.complete(Result.failure(IllegalStateException("failed")))
        advanceUntilIdle()
    }

    @Test
    fun lineDraftTargetReplacementRequiresConfirmationAndCancelKeepsText() = runTest(dispatcher) {
        val vm = replyViewModel(FakePullRequestsRepository())
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.LEFT))
        workspace(vm, PullRequestReviewWorkspaceAction.LineBody("Unsent"))
        workspace(vm, PullRequestReviewWorkspaceAction.CloseLine)
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.RIGHT))
        workspace(vm, PullRequestReviewWorkspaceAction.Cancel)
        assertEquals("Unsent", vm.state.value.reviewWorkspace.editor?.body)
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.RIGHT))
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        assertEquals(GithubDiffSide.RIGHT, vm.state.value.reviewWorkspace.editor?.side)
        assertEquals("", vm.state.value.reviewWorkspace.editor?.body)
    }

    @Test
    fun threadResolveRequiresLoadedPermissionsConfirmationAndSingleFlight() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubReviewThreadStatus>()
        repository.resolveResponse = { _, _ -> pending.await() }
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("unknown", true))
        assertEquals(null, vm.state.value.reviewWorkspace.confirmation)
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", true))
        workspace(vm, PullRequestReviewWorkspaceAction.Cancel)
        assertTrue(repository.resolveRequests.isEmpty())
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", true))
        repeat(2) { workspace(vm, PullRequestReviewWorkspaceAction.Confirm) }
        runCurrent()
        assertEquals(listOf("thread-1" to true), repository.resolveRequests)
        pending.complete(Result.success(GithubReviewThreadStatus("thread-1", 901, true, false, false, true)))
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.threads.single().isResolved)
        repository.resolveResponse = { _, _ -> Result.failure(IllegalStateException("denied")) }
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", false))
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.threads.single().isResolved)
        assertTrue("thread-1" in vm.state.value.reviewWorkspace.threadErrors)
        repository.threadsResponse = { Result.success(GithubReviewThreadPage(listOf(GithubReviewThreadStatus("thread-1", 901, true, false, false, false)), null)) }
        workspace(vm, PullRequestReviewWorkspaceAction.LoadThreads())
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", false))
        assertEquals(null, vm.state.value.reviewWorkspace.confirmation)
        assertEquals(2, repository.resolveRequests.size)
    }

    @Test
    fun lateBatchResultCannotEraseNextAccountsDraft() = verifyLateWorkspaceWrite(batch = true)

    @Test
    fun lateLineFailureCannotFailNextAccountsDraft() = verifyLateWorkspaceWrite(batch = false)

    private fun verifyLateWorkspaceWrite(batch: Boolean) = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val review = PendingResult<GithubPullRequestReview>()
        val line = PendingResult<GithubPullRequestReviewComment>()
        repository.batchResponse = { review.await() }
        repository.lineResponse = { line.await() }
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, GithubDiffSide.RIGHT))
        workspace(vm, PullRequestReviewWorkspaceAction.LineBody("Old account"))
        if (batch) {
            workspace(vm, PullRequestReviewWorkspaceAction.QueueLine)
            workspace(vm, PullRequestReviewWorkspaceAction.Open)
            workspace(vm, PullRequestReviewWorkspaceAction.Submit)
            workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        } else workspace(vm, PullRequestReviewWorkspaceAction.SendLine)
        runCurrent()
        vm.onSessionChanged(signedInSession("new", 2))
        advanceUntilIdle()
        queueLine(vm, GithubDiffSide.LEFT, "New account")
        val current = vm.state.value
        if (batch) review.complete(Result.success(GithubPullRequestReview(990, "Old", GithubReviewState.COMMENTED, user("reviewer"), null, "")))
        else line.complete(Result.failure(IllegalStateException("late")))
        advanceUntilIdle()
        assertEquals(current, vm.state.value)
        assertEquals("New account", vm.state.value.reviewWorkspace.comments.single().body)
    }

    @Test
    fun lateThreadReadCannotUndoResolvedState() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubReviewThreadPage>()
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        val old = vm.state.value.reviewWorkspace.threads.single()
        repository.threadsResponse = { pending.await() }
        workspace(vm, PullRequestReviewWorkspaceAction.LoadThreads())
        runCurrent()
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", true))
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.threads.single().isResolved)
        pending.complete(Result.success(GithubReviewThreadPage(listOf(old), null)))
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.threads.single().isResolved)
    }

    private fun workspace(vm: PullRequestDetailViewModel, action: PullRequestReviewWorkspaceAction) =
        vm.onAction(PullRequestDetailAction.ReviewWorkspace(action))

    @Test
    fun threadPaginationRejectsRepeatedCursorsAndKeepsLoadedPermissions() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val one = GithubReviewThreadStatus("thread-1", 901, false, false, true, true)
        val two = one.copy(id = "thread-2", rootCommentId = 902)
        repository.threadsResponse = { cursor -> Result.success(GithubReviewThreadPage(if (cursor == null) listOf(one) else listOf(two), "next")) }
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.LoadThreads(reset = false))
        advanceUntilIdle()
        assertTrue(vm.state.value.reviewWorkspace.threadsError)
        assertEquals(listOf(one), vm.state.value.reviewWorkspace.threads)
        repository.threadsResponse = { Result.success(GithubReviewThreadPage(listOf(two), null)) }
        workspace(vm, PullRequestReviewWorkspaceAction.LoadThreads(reset = false))
        advanceUntilIdle()
        assertEquals(setOf("thread-1", "thread-2"), vm.state.value.reviewWorkspace.threads.map { it.id }.toSet())
        assertEquals(null, vm.state.value.reviewWorkspace.nextCursor)
    }

    @Test
    fun oldAccountsLateThreadMutationCannotGrantTheNextAccountsPermissions() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val pending = PendingResult<GithubReviewThreadStatus>()
        repository.resolveResponse = { _, _ -> pending.await() }
        val vm = replyViewModel(repository)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        workspace(vm, PullRequestReviewWorkspaceAction.Resolve("thread-1", true))
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        runCurrent()
        repository.threadsResponse = { Result.success(GithubReviewThreadPage(listOf(GithubReviewThreadStatus("thread-1", 901, false, false, false, false)), null)) }
        vm.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        val current = vm.state.value
        pending.complete(Result.success(GithubReviewThreadStatus("thread-1", 901, true, false, true, true)))
        advanceUntilIdle()
        assertEquals(current, vm.state.value)
        assertFalse(vm.state.value.reviewWorkspace.threads.single().viewerCanResolve)
        assertTrue(vm.state.value.reviewWorkspace.threadBusy.isEmpty())
    }

    @Test
    fun oldReviewPageCannotChangeJustPublishedBatchReview() = runTest(dispatcher) {
        val repository = FakePullRequestsRepository()
        val oldPage = PendingResult<GithubPage<GithubPullRequestReview>>()
        var calls = 0
        val reads = object : GithubPullRequestsRepository by repository {
            override suspend fun reviews(owner: String, name: String, number: Int, page: Int, perPage: Int): Result<GithubPage<GithubPullRequestReview>> =
                if (++calls == 1) repository.reviews(owner, name, number, page, perPage).map { it.copy(nextPage = 2) } else oldPage.await()
        }
        val vm = replyViewModel(reads)
        vm.onSessionChanged(signedInSession("reviewer"))
        advanceUntilIdle()
        vm.onAction(PullRequestDetailAction.LoadMoreReviews)
        runCurrent()
        queueLine(vm, GithubDiffSide.RIGHT, "New review")
        workspace(vm, PullRequestReviewWorkspaceAction.Open)
        workspace(vm, PullRequestReviewWorkspaceAction.Submit)
        workspace(vm, PullRequestReviewWorkspaceAction.Confirm)
        advanceUntilIdle()
        assertEquals(990L, vm.state.value.reviews.first().id)
        val confirmed = vm.state.value.reviews
        oldPage.complete(Result.success(GithubPage(listOf(confirmed.first().copy(id = 991)), null)))
        advanceUntilIdle()
        assertEquals(990L, vm.state.value.reviews.first().id)
        assertEquals(confirmed, vm.state.value.reviews)
    }

    private fun queueLine(vm: PullRequestDetailViewModel, side: GithubDiffSide, text: String) {
        workspace(vm, PullRequestReviewWorkspaceAction.OpenLine("app/Main.kt", 1, side))
        workspace(vm, PullRequestReviewWorkspaceAction.LineBody(text))
        workspace(vm, PullRequestReviewWorkspaceAction.QueueLine)
    }

    private fun replyViewModel(repository: GithubPullRequestsRepository, saved: SavedStateHandle = SavedStateHandle()) =
        PullRequestDetailViewModel("openai", "codex", 1, repository, FakeIssuesRepository(), TestGithubRepositoryDetailsRepository(), saved)

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

    @Test
    fun privatePullRequestListCannotReturnFromOldAccountAfterBackstackSwitch() = runTest(dispatcher) {
        val first = signedInSession("same", 1)
        val sessions = MutableStateFlow<GithubSession>(first)
        val base = FakePullRequestsRepository()
        lateinit var result: Continuation<Result<GithubPage<GithubPullRequest>>>
        val repository = object : GithubPullRequestsRepository by base {
            override suspend fun pullRequests(owner: String, name: String, query: GithubPullRequestListQuery, page: Int, perPage: Int): Result<GithubPage<GithubPullRequest>> =
                if ((sessions.value as? GithubSession.SignedIn)?.account?.id == 1L) suspendCoroutine { result = it }
                else Result.failure(IllegalStateException("private pull requests denied"))
        }
        val vm = PullRequestsViewModel("o", "r", repository, sessions)
        runCurrent()
        sessions.value = signedInSession("same", 2)
        runCurrent()
        result.resume(base.pullRequests("o", "r", GithubPullRequestListQuery(GithubPullRequestState.OPEN), 1, 30))
        runCurrent()
        assertEquals(2L, vm.state.value.viewerAccountId)
        assertTrue(vm.state.value.items.isEmpty())
    }

    private class FakePullRequestsRepository : GithubPullRequestsRepository {
        val pullRequestPages = mutableListOf<Int>()
        val pullRequestQueries = mutableListOf<GithubPullRequestListQuery>()
        var submittedReview: GithubPullRequestReviewDraft? = null
        var updatedDraft: GithubPullRequestDraft? = null
        var requestedReviewersUpdate: GithubRequestedReviewersUpdate? = null
        var mergeDraft: GithubMergeDraft? = null
        var snapshotResponse: suspend () -> Result<GithubPullRequest> = { Result.success(pullRequest(1, GithubPullRequestState.OPEN)) }
        val lineRequests = mutableListOf<Pair<String, GithubReviewLineComment>>()
        val batchRequests = mutableListOf<GithubReviewSubmissionDraft>()
        val resolveRequests = mutableListOf<Pair<String, Boolean>>()
        var lineResponse: suspend (GithubReviewLineComment) -> Result<GithubPullRequestReviewComment> = { comment ->
            Result.success(reviewComments("openai", "codex", 1, 1, 30).getOrThrow().items.single().copy(id = 990, body = comment.body))
        }
        var batchResponse: suspend (GithubReviewSubmissionDraft) -> Result<GithubPullRequestReview> = { draft ->
            Result.success(GithubPullRequestReview(990, draft.body.orEmpty(), GithubReviewState.COMMENTED, user("reviewer"), null, "https://github.com/openai/codex/pull/1"))
        }
        var threadsResponse: suspend (String?) -> Result<GithubReviewThreadPage> = {
            Result.success(GithubReviewThreadPage(listOf(GithubReviewThreadStatus("thread-1", 901, false, false, true, true)), null))
        }
        var resolveResponse: suspend (String, Boolean) -> Result<GithubReviewThreadStatus> = { id, resolved ->
            Result.success(GithubReviewThreadStatus(id, 901, resolved, false, !resolved, resolved))
        }

        override suspend fun reviewSnapshot(owner: String, name: String, number: Int) = snapshotResponse()
        override suspend fun createLineComment(owner: String, name: String, number: Int, commitSha: String, comment: GithubReviewLineComment): Result<GithubPullRequestReviewComment> {
            lineRequests += commitSha to comment
            return lineResponse(comment)
        }
        override suspend fun submitReviewBatch(owner: String, name: String, number: Int, draft: GithubReviewSubmissionDraft): Result<GithubPullRequestReview> {
            batchRequests += draft
            return batchResponse(draft)
        }
        override suspend fun reviewThreadStatuses(owner: String, name: String, number: Int, cursor: String?) = threadsResponse(cursor)
        override suspend fun setReviewThreadResolved(threadId: String, resolved: Boolean): Result<GithubReviewThreadStatus> {
            resolveRequests += threadId to resolved
            return resolveResponse(threadId, resolved)
        }
        val replyRequests = mutableListOf<GithubReviewCommentReplyDraft>()
        var replyResponse: suspend (GithubReviewCommentReplyDraft) -> Result<GithubPullRequestReviewComment> = {
            Result.failure(UnsupportedOperationException())
        }

        override suspend fun replyToReviewComment(owner: String, name: String, number: Int, draft: GithubReviewCommentReplyDraft): Result<GithubPullRequestReviewComment> {
            replyRequests += draft
            return replyResponse(draft)
        }

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
                            patch = "@@ -1 +1 @@\n-old\n+new",
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
