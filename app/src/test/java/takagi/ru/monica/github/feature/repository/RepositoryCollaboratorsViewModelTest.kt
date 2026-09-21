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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySettings
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubWebhookEdit
import takagi.ru.monica.github.domain.GithubWebhookDelivery
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.signedInGithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryCollaboratorsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun collaboratorsAppendWithoutDuplicatesAndCanBeFiltered() = runTest(dispatcher) {
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", FakeRepository())
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.LoadMore)
        advanceUntilIdle()
        viewModel.onAction(RepositoryCollaboratorsAction.Search("bob"))

        assertEquals(listOf("alice", "bob"), viewModel.state.value.items.map { it.user.login })
        assertEquals(listOf("bob"), viewModel.state.value.filteredItems.map { it.user.login })
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun refreshReloadsFirstPageAndClearsRefreshingState() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.Refresh)
        advanceUntilIdle()

        assertEquals(2, repository.firstPageCalls)
        assertEquals(listOf("alice"), viewModel.state.value.items.map { it.user.login })
        assertFalse(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun retryAfterFailedRefreshReplaysFirstPageAndKeepsRows() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        advanceUntilIdle()

        repository.failFirstPage = true
        viewModel.onAction(RepositoryCollaboratorsAction.Refresh)
        advanceUntilIdle()
        assertEquals(listOf("alice"), viewModel.state.value.items.map { it.user.login })
        assertTrue(viewModel.state.value.error)

        repository.failFirstPage = false
        viewModel.onAction(RepositoryCollaboratorsAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 1, 1), repository.requested)
        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun retryAfterFailedPaginationReplaysSamePage() = runTest(dispatcher) {
        val repository = FakeRepository(failSecondPage = true)
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.LoadMore)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error)

        repository.failSecondPage = false
        viewModel.onAction(RepositoryCollaboratorsAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 2), repository.requested)
        assertEquals(listOf("alice", "bob"), viewModel.state.value.items.map { it.user.login })
    }

    @Test
    fun managementNeedsAdminRoleAndAKnownRoleOnTheRow() = runTest(dispatcher) {
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", FakeRepository())
        advanceUntilIdle()
        val adminRow = collaborator("carol", GithubCollaboratorRole.ADMIN)

        assertFalse(viewModel.state.value.canManage)
        assertFalse(viewModel.state.value.isManageable(adminRow))

        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canManage)
        assertTrue(viewModel.state.value.isManageable(adminRow))
        // A contributor row comes from the public fallback list and says nothing about access.
        assertFalse(
            viewModel.state.value.isManageable(
                collaborator("dave", GithubCollaboratorRole.UNKNOWN, contributions = 7)
            )
        )
    }

    @Test
    fun roleChangeSendsThePermissionAndRereadsTheList() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("bob", GithubCollaboratorRole.TRIAGE))
        advanceUntilIdle()

        assertEquals(listOf("bob" to "triage"), repository.invites.map { it.login to it.permission })
        assertEquals(
            RepositoryCollaboratorFeedback("bob", RepositoryCollaboratorOutcome.AccessUpdated),
            viewModel.state.value.outcome
        )
        assertEquals(2, repository.firstPageCalls)
        assertFalse(viewModel.state.value.isWriting)
    }

    @Test
    fun createdInvitationIsReportedAsAnInvitation() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.inviteResult = Result.success(GithubCollaboratorChange.Invited)
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("bob", GithubCollaboratorRole.WRITE))
        advanceUntilIdle()

        assertEquals(
            RepositoryCollaboratorOutcome.InvitationSent,
            viewModel.state.value.outcome?.outcome
        )
    }

    @Test
    fun roleChangeIsRefusedForViewersWhoCannotAdmin() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.viewerRole = GithubCollaboratorRole.READ
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("bob", GithubCollaboratorRole.ADMIN))
        advanceUntilIdle()

        assertTrue(repository.invites.isEmpty())
        assertNull(viewModel.state.value.outcome)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun refusedRoleChangeNamesTheReasonAndKeepsTheRows() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.inviteResult = Result.failure(GithubApiException(statusCode = 403))
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("bob", GithubCollaboratorRole.WRITE))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.failure)
        assertNull(viewModel.state.value.outcome)
        assertEquals(listOf("alice"), viewModel.state.value.items.map { it.user.login })
        assertFalse(viewModel.state.value.isWriting)
    }

    @Test
    fun removalDropsTheRowOnTheRereadAndReportsIt() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(RepositoryCollaboratorsAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf("alice", "bob"), viewModel.state.value.items.map { it.user.login })

        viewModel.onAction(RepositoryCollaboratorsAction.Remove("bob"))
        advanceUntilIdle()

        assertEquals(listOf("bob"), repository.removals)
        assertEquals(
            RepositoryCollaboratorOutcome.Removed,
            viewModel.state.value.outcome?.outcome
        )
        assertEquals(listOf("alice"), viewModel.state.value.items.map { it.user.login })
    }

    @Test
    fun dismissedFeedbackIsNotReportedAgain() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(RepositoryCollaboratorsAction.Remove("alice"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.outcome != null)

        viewModel.onAction(RepositoryCollaboratorsAction.DismissFeedback)

        assertNull(viewModel.state.value.outcome)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun switchingAccountsRevokesManagementBeforeTheRoleReloads() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canManage)

        repository.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInGithubSession("reader"))

        assertFalse(viewModel.state.value.canManage)
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("bob", GithubCollaboratorRole.ADMIN))
        advanceUntilIdle()

        assertTrue(repository.invites.isEmpty())
        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canManage)
    }

    @Test
    fun pushRoleCannotManageCollaboratorsBecauseAdminIsRequired() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.viewerRole = GithubCollaboratorRole.WRITE
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", repository)
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canManage)
        assertFalse(viewModel.state.value.isManageable(collaborator("alice", GithubCollaboratorRole.WRITE)))

        viewModel.onAction(RepositoryCollaboratorsAction.ChangeRole("alice", GithubCollaboratorRole.ADMIN))
        advanceUntilIdle()

        assertTrue(repository.invites.isEmpty())
    }

    @Test
    fun unknownRoleRowsAreNotManageableEvenForAdmins() = runTest(dispatcher) {
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", FakeRepository())
        viewModel.onSessionChanged(signedInGithubSession("joyins"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canManage)

        assertFalse(viewModel.state.value.isManageable(collaborator("stranger", GithubCollaboratorRole.UNKNOWN)))
    }

    private class FakeRepository(var failSecondPage: Boolean = false) : GithubRepositoryDetailsRepository {
        var firstPageCalls: Int = 0
        var failFirstPage = false
        val requested = mutableListOf<Int>()
        var viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
        var inviteResult: Result<GithubCollaboratorChange> =
            Result.success(GithubCollaboratorChange.Updated)
        var removeResult: Result<Unit> = Result.success(Unit)
        val invites = mutableListOf<GithubCollaboratorInvite>()
        val removals = mutableListOf<String>()

        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) = when (page) {
            1 -> {
                requested += page
                if (failFirstPage) {
                    Result.failure(IllegalStateException("Offline"))
                } else {
                    firstPageCalls++
                    Result.success(GithubPage(listOf(collaborator("alice", GithubCollaboratorRole.ADMIN)), 2))
                }
            }
            else -> {
                requested += page
                if (failSecondPage) {
                    Result.failure(IllegalStateException("Offline"))
                } else {
                    Result.success(
                        GithubPage(
                            listOf(
                                collaborator("alice", GithubCollaboratorRole.ADMIN),
                                collaborator("bob", GithubCollaboratorRole.WRITE)
                            ),
                            null
                        )
                    )
                }
            }
        }

        override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> =
            Result.success(TestGithubRepositoryDetailsRepository.details(owner, name, viewerRole))

        override suspend fun setCollaborator(
            owner: String,
            name: String,
            invite: GithubCollaboratorInvite
        ): Result<GithubCollaboratorChange> {
            invites += invite
            return inviteResult
        }

        override suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit> {
            removals += login
            return removeResult
        }

        override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)

        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            Result.success<GithubBranchProtection?>(null)

        override suspend fun updateTopics(owner: String, name: String, topics: List<String>) =
            Result.success(topics)

        override suspend fun updateSettings(owner: String, name: String, edit: GithubRepositorySettingsEdit) =
            Result.success(
                GithubRepositorySettings(
                    isPrivate = edit.isPrivate == true,
                    isArchived = edit.isArchived == true
                )
            )

        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
        override suspend fun webhookDeliveries(
            owner: String, name: String, id: Long, page: Int, perPage: Int
        ): Result<GithubPage<GithubWebhookDelivery>> = Result.failure(UnsupportedOperationException())
        override suspend fun redeliverWebhook(owner: String, name: String, id: Long, deliveryId: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateWebhook(
            owner: String, name: String, id: Long, edit: GithubWebhookEdit
        ): Result<GithubRepositoryWebhook> = Result.failure(UnsupportedOperationException())
        override suspend fun deleteWebhook(owner: String, name: String, id: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private companion object {
        fun collaborator(
            login: String,
            role: GithubCollaboratorRole,
            contributions: Int? = null
        ) = GithubCollaborator(
            user = GithubUserSummary(login, null, "https://github.com/$login"),
            role = role,
            contributions = contributions
        )
    }
}
