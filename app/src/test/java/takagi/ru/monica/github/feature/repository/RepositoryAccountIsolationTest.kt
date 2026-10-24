package takagi.ru.monica.github.feature.repository

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryAccountIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = signedInGithubSession()
    private val second = GithubSession.SignedIn(first.account.copy(id = 2, login = "reader"))

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun switchingAccountsImmediatelyRevokesBranchAndTagWriteAccess() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val branches = RepositoryBranchesViewModel("o", "r", "main", Contents(), details)
        val tags = RepositoryTagsViewModel("o", "r", "main", Contents(), details)
        branches.onSessionChanged(first); tags.onSessionChanged(first)
        advanceUntilIdle()
        assertTrue(branches.state.value.canWrite); assertTrue(tags.state.value.canWrite)
        details.viewerRole = GithubCollaboratorRole.READ
        branches.onSessionChanged(second); tags.onSessionChanged(second)
        assertFalse(branches.state.value.canWrite); assertFalse(tags.state.value.canWrite)
        assertTrue(branches.state.value.items.isEmpty()); assertTrue(tags.state.value.items.isEmpty())
        advanceUntilIdle()
        assertFalse(branches.state.value.canWrite); assertFalse(tags.state.value.canWrite)
    }

    @Test fun fileContentAndTreeDisappearBeforeAnotherAccountsRequestsComplete() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val contents = Contents()
        val tree = RepositoryFilesViewModel("o", "r", "main", "", contents, details)
        val file = RepositoryFileViewModel("o", "r", "main", "secret.txt", contents, details)
        tree.onSessionChanged(first); file.onSessionChanged(first)
        advanceUntilIdle()
        file.onAction(RepositoryFileAction.StartEdit)
        file.onAction(RepositoryFileAction.DraftChanged("Unpublished account A text"))
        assertNotNull(file.state.value.content)
        assertTrue(tree.state.value.items.isNotEmpty())
        details.viewerRole = GithubCollaboratorRole.READ
        tree.onSessionChanged(second); file.onSessionChanged(second)
        assertNull(file.state.value.content)
        assertNull(file.state.value.draftText)
        assertFalse(file.state.value.editing)
        assertFalse(file.state.value.canWrite)
        assertTrue(tree.state.value.items.isEmpty())
        assertTrue(tree.state.value.branches.items.isEmpty())
        assertFalse(tree.state.value.canWrite)
        advanceUntilIdle()
        file.onSessionChanged(first)
        assertEquals("Unpublished account A text", file.state.value.draftText)
        assertTrue(file.state.value.editing)
        advanceUntilIdle()
    }

    @Test fun cancelledPrivateReadCannotRepopulateTheNextAccountsFile() = runTest(dispatcher) {
        val delayed = CompletableDeferred<Result<GithubFileContent>>()
        val contents = Contents().apply { fileResponse = { withContext(NonCancellable) { delayed.await() } } }
        val file = RepositoryFileViewModel("o", "r", "main", "secret.txt", contents, TestGithubRepositoryDetailsRepository())
        file.onSessionChanged(first)
        runCurrent()
        contents.fileResponse = { Result.failure(IllegalStateException("not accessible")) }
        file.onSessionChanged(second)
        runCurrent()
        assertNull(file.state.value.content)
        delayed.complete(Result.success(GithubFileContent.Text("Account A private bytes")))
        advanceUntilIdle()
        assertNull(file.state.value.content)
        assertTrue(file.state.value.error)
        assertEquals("reader", file.state.value.viewerLogin)
    }

    @Test fun collaboratorAndWebhookAdministrationAreRecheckedForEachAccount() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val collaborators = RepositoryCollaboratorsViewModel("o", "r", details)
        val hooks = RepositoryWebhooksViewModel("o", "r", details, viewerCanAdmin = true)
        collaborators.onSessionChanged(first); hooks.onSessionChanged(first)
        advanceUntilIdle()
        assertTrue(collaborators.state.value.canManage); assertTrue(hooks.state.value.canManage)
        details.viewerRole = GithubCollaboratorRole.READ
        collaborators.onSessionChanged(second); hooks.onSessionChanged(second)
        assertFalse(collaborators.state.value.canManage); assertFalse(hooks.state.value.canManage)
        hooks.onAction(RepositoryWebhooksAction.Delete(11))
        collaborators.onAction(RepositoryCollaboratorsAction.Remove("someone"))
        advanceUntilIdle()
        assertFalse(collaborators.state.value.canManage); assertFalse(hooks.state.value.canManage)
        assertTrue(details.removals.isEmpty())
    }

    @Test fun invitationDraftAndPermissionsBelongToTheirAccount() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val invite = InviteCollaboratorViewModel("o", "r", details)
        invite.onSessionChanged(first)
        advanceUntilIdle()
        invite.onAction(InviteCollaboratorAction.LoginChanged("teammate"))
        assertTrue(invite.state.value.canSubmit)
        details.viewerRole = GithubCollaboratorRole.READ
        invite.onSessionChanged(second)
        assertEquals("", invite.state.value.login)
        assertFalse(invite.state.value.canSubmit)
        invite.onAction(InviteCollaboratorAction.LoginChanged("other"))
        invite.onAction(InviteCollaboratorAction.Submit)
        advanceUntilIdle()
        assertTrue(details.invites.isEmpty())
        details.viewerRole = GithubCollaboratorRole.ADMIN
        invite.onSessionChanged(first)
        assertEquals("teammate", invite.state.value.login)
        assertFalse(invite.state.value.canSubmit)
        advanceUntilIdle()
        assertTrue(invite.state.value.canSubmit)
    }

    @Test fun restoredInvitationDraftDoesNotAppearUnderAnotherAccount() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val source = InviteCollaboratorViewModel("o", "r", TestGithubRepositoryDetailsRepository(), saved)
        source.onSessionChanged(first)
        advanceUntilIdle()
        source.onAction(InviteCollaboratorAction.LoginChanged("private-teammate"))
        val other = InviteCollaboratorViewModel("o", "r", TestGithubRepositoryDetailsRepository(), saved)
        assertEquals("", other.state.value.login)
        other.onSessionChanged(second)
        advanceUntilIdle()
        assertEquals("", other.state.value.login)
        other.onSessionChanged(first)
        assertEquals("private-teammate", other.state.value.login)
        advanceUntilIdle()
    }

    @Test fun offscreenSessionObserverCancelsOldResolutionAndSurvivesRepeatedSwitches() = runTest(dispatcher) {
        val sessions = MutableStateFlow<GithubSession>(first)
        val delayed = CompletableDeferred<Result<String>>()
        val contents = Contents().apply { resolveResponse = { withContext(NonCancellable) { delayed.await() } } }
        val details = TestGithubRepositoryDetailsRepository()
        val branches = RepositoryBranchesViewModel("o", "r", "main", contents, details, sessions)
        advanceUntilIdle()
        branches.onAction(RepositoryBranchesAction.CreateBranch("feature/new", "main"))
        runCurrent()
        assertNotNull(branches.state.value.pendingWrite)
        // No route callback: the ViewModel is retained on the navigation back stack.
        details.viewerRole = GithubCollaboratorRole.READ
        sessions.value = second
        runCurrent()
        assertFalse(branches.state.value.canWrite)
        assertNull(branches.state.value.pendingWrite)
        delayed.complete(Result.success("a".repeat(40)))
        advanceUntilIdle()
        assertEquals(0, contents.creates)
        details.viewerRole = GithubCollaboratorRole.ADMIN
        sessions.value = first
        advanceUntilIdle()
        assertTrue(branches.state.value.canWrite)
        assertEquals("joyins", branches.state.value.viewerLogin)
        sessions.value = GithubSession.SignedOut
        advanceUntilIdle()
        assertFalse(branches.state.value.canWrite)
    }

    private class Contents : GithubRepositoryContentsRepository {
        var creates = 0
        var resolveResponse: suspend () -> Result<String> = { Result.success("a".repeat(40)) }
        var fileResponse: suspend () -> Result<GithubFileContent> = { Result.success(GithubFileContent.Text("private", "a".repeat(40))) }
        override suspend fun resolveRef(owner: String, name: String, ref: String) = resolveResponse()
        override suspend fun createBranch(owner: String, name: String, branch: String, fromSha: String): Result<GithubBranch> {
            creates++
            return Result.success(GithubBranch(branch, fromSha, false))
        }
        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(GithubBranch("main", "a".repeat(40), false)), null))
        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(GithubTag("v1", "a".repeat(40))), null))
        override suspend fun directory(owner: String, name: String, path: String, ref: String?) =
            Result.success(listOf(GithubContentItem("secret.txt", "secret.txt", "a".repeat(40), 7, GithubContentType.FILE, null, null)))
        override suspend fun file(owner: String, name: String, path: String, ref: String?) = fileResponse()
    }
}
