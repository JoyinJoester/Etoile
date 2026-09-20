package takagi.ru.monica.github.feature.repository

import androidx.lifecycle.SavedStateHandle
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
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class InviteCollaboratorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestGithubRepositoryDetailsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        InviteCollaboratorViewModel("openai", "codex", repository, saved)

    @Test
    fun anUnavailableLoginNeverReachesTheRepository() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(InviteCollaboratorAction.LoginChanged("   "))
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onAction(InviteCollaboratorAction.Submit)

        viewModel.onAction(InviteCollaboratorAction.LoginChanged("-bob"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showLoginHint)
        assertFalse(viewModel.state.value.canSubmit)
        assertTrue(repository.invites.isEmpty())
    }

    @Test
    fun inviteSendsTheTrimmedLoginWithTheChosenPermission() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(InviteCollaboratorAction.LoginChanged("  bob "))
        viewModel.onAction(InviteCollaboratorAction.RoleChanged(GithubCollaboratorRole.READ))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertEquals(listOf("bob" to "pull"), repository.invites.map { it.login to it.permission })
        assertEquals(
            RepositoryCollaboratorFeedback("bob", RepositoryCollaboratorOutcome.InvitationSent),
            viewModel.state.value.feedback
        )
        assertEquals("", viewModel.state.value.login)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun restatedPermissionIsReportedAsAnUpdate() = runTest(dispatcher) {
        repository.inviteResult = Result.success(GithubCollaboratorChange.Updated)
        val viewModel = viewModel()
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertEquals(
            RepositoryCollaboratorOutcome.AccessUpdated,
            viewModel.state.value.feedback?.outcome
        )
    }

    @Test
    fun refusedInviteKeepsTheTypedLoginAndNamesTheReason() = runTest(dispatcher) {
        repository.inviteResult = Result.failure(GithubApiException(statusCode = 422))
        val viewModel = viewModel()
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.InvalidInput, viewModel.state.value.failure)
        assertEquals("bob", viewModel.state.value.login)
        assertNull(viewModel.state.value.feedback)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun draftLoginAndRoleComeBackFromSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle(
            mapOf(
                "invite_collaborator_login" to "octocat",
                "invite_collaborator_role" to GithubCollaboratorRole.MAINTAIN.name
            )
        )

        val viewModel = viewModel(saved)
        advanceUntilIdle()

        assertEquals("octocat", viewModel.state.value.login)
        assertEquals(GithubCollaboratorRole.MAINTAIN, viewModel.state.value.role)

        repository.inviteResult = Result.failure(GithubApiException(statusCode = 403))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertEquals("octocat", saved.get<String>("invite_collaborator_login"))
        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.failure)
    }

    @Test
    fun aRoleTheApiCannotGrantFallsBackToTheDefaultChoice() = runTest(dispatcher) {
        val viewModel = viewModel(
            SavedStateHandle(mapOf("invite_collaborator_role" to GithubCollaboratorRole.UNKNOWN.name))
        )

        assertEquals(GithubCollaboratorRole.WRITE, viewModel.state.value.role)
    }

    @Test
    fun editsWhileAnInviteIsInFlightAreIgnored() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        assertTrue(viewModel.state.value.isSubmitting)

        viewModel.onAction(InviteCollaboratorAction.LoginChanged("someone-else"))
        advanceUntilIdle()

        assertEquals(listOf("bob"), repository.invites.map { it.login })
        assertEquals("bob", viewModel.state.value.feedback?.login)
    }

    @Test
    fun consumedFeedbackIsNotReportedAgain() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.feedback != null)

        viewModel.onAction(InviteCollaboratorAction.ConsumeFeedback)

        assertNull(viewModel.state.value.feedback)
    }

    @Test
    fun doubleSubmitOnlySendsOneInvite() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        viewModel.onAction(InviteCollaboratorAction.Submit)
        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertEquals(1, repository.invites.size)
    }

    @Test
    fun loginChangeIsPersistedAsDraftInSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)

        viewModel.onAction(InviteCollaboratorAction.LoginChanged("octocat"))

        assertEquals("octocat", saved.get<String>("invite_collaborator_login"))
    }

    @Test
    fun successfulInviteClearsTheDraftLoginFromSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)
        viewModel.onAction(InviteCollaboratorAction.LoginChanged("bob"))
        assertEquals("bob", saved.get<String>("invite_collaborator_login"))

        viewModel.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()

        assertNull(saved.get<String>("invite_collaborator_login"))
    }
}
