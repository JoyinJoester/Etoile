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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubRepositoryPermissionsDto
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryViewerState
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerPermissionGatingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun permissionsDtoMapsToTheHighestGrantedRole() {
        assertEquals(
            GithubCollaboratorRole.ADMIN,
            GithubRepositoryPermissionsDto(pull = true, push = true, admin = true).toRole()
        )
        assertEquals(
            GithubCollaboratorRole.MAINTAIN,
            GithubRepositoryPermissionsDto(pull = true, push = true, maintain = true).toRole()
        )
        assertEquals(
            GithubCollaboratorRole.WRITE,
            GithubRepositoryPermissionsDto(pull = true, push = true).toRole()
        )
        assertEquals(
            GithubCollaboratorRole.TRIAGE,
            GithubRepositoryPermissionsDto(pull = true, triage = true).toRole()
        )
        assertEquals(GithubCollaboratorRole.READ, GithubRepositoryPermissionsDto(pull = true).toRole())
        assertEquals(GithubCollaboratorRole.UNKNOWN, GithubRepositoryPermissionsDto().toRole())
    }

    @Test
    fun triageImpliesWriteButWriteDoesNotImplyAdmin() {
        assertTrue(GithubCollaboratorRole.TRIAGE.canTriage)
        assertFalse(GithubCollaboratorRole.TRIAGE.canPush)
        assertTrue(GithubCollaboratorRole.WRITE.canTriage)
        assertTrue(GithubCollaboratorRole.WRITE.canPush)
        assertFalse(GithubCollaboratorRole.WRITE.canAdmin)
        assertTrue(GithubCollaboratorRole.ADMIN.canAdmin)
        assertFalse(GithubCollaboratorRole.READ.canTriage)
        assertFalse(GithubCollaboratorRole.UNKNOWN.canTriage)
    }

    @Test
    fun readOnlyViewerCannotEditTopicsOrReachAdminSections() {
        val state = stateFor(GithubCollaboratorRole.READ)

        assertFalse(state.canEditTopics)
        assertFalse(state.canAdminister)
        assertFalse(state.canManageSettings)
    }

    @Test
    fun writeViewerEditsTopicsButAdminSectionsStayLocked() {
        val state = stateFor(GithubCollaboratorRole.WRITE)

        assertTrue(state.canEditTopics)
        assertFalse(state.canAdminister)
        assertFalse(state.canManageSettings)
    }

    @Test
    fun adminViewerUnlocksTopicsAndAdminSections() {
        val state = stateFor(GithubCollaboratorRole.ADMIN)

        assertTrue(state.canEditTopics)
        assertTrue(state.canAdminister)
        assertTrue(state.canManageSettings)
    }

    @Test
    fun signedOutViewerIsNeverGrantedWriteEvenWithACachedRole() {
        val state = stateFor(GithubCollaboratorRole.ADMIN, viewerLogin = null)

        assertFalse(state.canEditTopics)
        assertFalse(state.canAdminister)
        assertFalse(state.canManageSettings)
    }

    @Test
    fun readOnlyViewerCannotUpdateTopicsThroughTheViewModel() = runTest(dispatcher) {
        val repository = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.READ)
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, NoopActionsRepository())
        advanceUntilIdle()
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("android")))
        advanceUntilIdle()

        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canEditTopics)
        assertFalse(viewModel.state.value.isUpdatingTopics)
        assertEquals(null, viewModel.state.value.topicsFailure)
    }

    @Test
    fun writeViewerStillUpdatesTopicsThroughTheViewModel() = runTest(dispatcher) {
        val repository = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.WRITE)
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, NoopActionsRepository())
        advanceUntilIdle()
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("android")))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canEditTopics)
        assertEquals(listOf("android"), viewModel.state.value.details?.topics)
        assertFalse(viewModel.state.value.isUpdatingTopics)
    }

    @Test
    fun maintainViewerCanAdministerSectionsButCannotArchive() = runTest(dispatcher) {
        val repository = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.MAINTAIN)
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, NoopActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canAdminister)
        assertFalse(viewModel.state.value.canManageSettings)

        viewModel.onAction(RepositoryDetailAction.SetArchived(isArchived = true))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)
        assertEquals(false, viewModel.state.value.details?.isArchived)
    }

    @Test
    fun switchingDirectlyFromAdminToReaderRevokesPermissionsBeforeTheReloadFinishes() = runTest(dispatcher) {
        val repository = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.ADMIN)
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, NoopActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canAdminister)
        assertTrue(viewModel.state.value.canEditTopics)

        repository.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(GithubSession.SignedIn(account().copy(id = 2, login = "reader")))

        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canAdminister)
        assertFalse(viewModel.state.value.canEditTopics)
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("must-not-save")))
        advanceUntilIdle()

        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canAdminister)
        assertFalse(viewModel.state.value.canEditTopics)
        assertTrue(viewModel.state.value.details?.topics.isNullOrEmpty())
    }

    private fun stateFor(
        role: GithubCollaboratorRole,
        viewerLogin: String? = "joyins"
    ) = RepositoryDetailUiState(
        owner = "openai",
        name = "codex",
        details = TestGithubRepositoryDetailsRepository.details(viewerRole = role),
        viewerLogin = viewerLogin
    )

    private fun account() = GithubAccount(
        id = 1,
        login = "joyins",
        name = null,
        bio = null,
        avatarUrl = "https://avatars.githubusercontent.com/u/1",
        htmlUrl = "https://github.com/joyins",
        publicRepositories = 0,
        followers = 0,
        following = 0
    )

    private class NoopActionsRepository : GithubRepositoryActionsRepository {
        override suspend fun viewerState(owner: String, name: String) =
            Result.success(GithubRepositoryViewerState(isStarred = false, isWatching = false))

        override suspend fun setStarred(owner: String, name: String, starred: Boolean) =
            Result.success(starred)

        override suspend fun setWatching(owner: String, name: String, watching: Boolean) =
            Result.success(watching)

        override suspend fun fork(owner: String, name: String) =
            Result.success(TestGithubRepositoryDetailsRepository.details().repository)
    }
}
