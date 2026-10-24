package takagi.ru.monica.github.feature.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.signedInGithubSession
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryCreateDraft
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRepositoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        CreateRepositoryViewModel(source, saved)

    @Test
    fun anUnavailableNameNeverReachesTheRepository() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CreateRepositoryAction.NameChanged("  "))
        viewModel.onAction(CreateRepositoryAction.Submit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onAction(CreateRepositoryAction.NameChanged("bad name!"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canSubmit)
        assertTrue(viewModel.state.value.showNameHint)
        assertEquals(emptyList<GithubRepositoryCreateDraft>(), source.drafts)
    }

    @Test
    fun submittingSendsTheTrimmedDraftAndHandsOverTheCreatedRepository() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CreateRepositoryAction.NameChanged("  demo-app  "))
        viewModel.onAction(CreateRepositoryAction.DescriptionChanged("  a demo  "))
        viewModel.onAction(CreateRepositoryAction.TogglePrivate)
        assertTrue(viewModel.state.value.canSubmit)
        viewModel.onAction(CreateRepositoryAction.Submit)
        assertTrue(viewModel.state.value.isSubmitting)
        advanceUntilIdle()

        val sent = source.drafts.single()
        assertEquals("demo-app", sent.name)
        assertEquals("a demo", sent.description)
        assertTrue(sent.isPrivate)
        assertFalse(sent.autoInit)
        assertEquals("joyins/demo-app", viewModel.state.value.created?.fullName)

        viewModel.onAction(CreateRepositoryAction.ConsumeCreated)
        assertNull(viewModel.state.value.created)
        assertEquals("", viewModel.state.value.name)
    }

    @Test
    fun aRefusedCreationKeepsTheTypedNameAndNamesTheReason() = runTest(dispatcher) {
        source.result = Result.failure(GithubApiException(statusCode = 422))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CreateRepositoryAction.NameChanged("demo-app"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.InvalidInput, viewModel.state.value.failure)
        assertEquals("demo-app", viewModel.state.value.name)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun editingAfterARefusalClearsTheExplanation() = runTest(dispatcher) {
        source.result = Result.failure(GithubApiException(statusCode = 403))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CreateRepositoryAction.NameChanged("demo-app"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        advanceUntilIdle()
        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.failure)

        viewModel.onAction(CreateRepositoryAction.DescriptionChanged("now with a description"))

        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun inputIsIgnoredWhileACreationIsInFlight() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CreateRepositoryAction.NameChanged("demo-app"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        viewModel.onAction(CreateRepositoryAction.NameChanged("edited mid-flight"))

        assertTrue(viewModel.state.value.isSubmitting)
        assertEquals("demo-app", viewModel.state.value.name)
        advanceUntilIdle()
        assertEquals(1, source.drafts.size)
    }

    @Test
    fun aRestoredDraftCarriesEveryFieldBack() = runTest(dispatcher) {
        val saved = SavedStateHandle(
            mapOf(
                "create_repository_name" to "demo-app",
                "create_repository_description" to "a demo",
                "create_repository_private" to true,
                "create_repository_auto_init" to true
            )
        )

        val viewModel = viewModel(saved)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canSubmit)
        assertTrue(viewModel.state.value.isPrivate)
        viewModel.onAction(CreateRepositoryAction.Submit)
        advanceUntilIdle()

        assertEquals(
            GithubRepositoryCreateDraft.fromInput("demo-app", "a demo", true, true).getOrThrow().name,
            source.drafts.single().name
        )
        assertTrue(source.drafts.single().autoInit)
    }

    @Test
    fun accountDraftsStaySeparateAcrossSwitchesAndStateRestoration() = runTest(dispatcher) {
        val alice = signedInGithubSession("alice")
        val bob = GithubSession.SignedIn(alice.account.copy(id = 2, login = "bob"))
        val sessions = MutableStateFlow<GithubSession>(alice)
        val saved = SavedStateHandle()
        val viewModel = CreateRepositoryViewModel(source, saved, sessions)
        viewModel.onAction(CreateRepositoryAction.NameChanged("alice-private-draft"))
        viewModel.onAction(CreateRepositoryAction.TogglePrivate)
        sessions.value = bob
        runCurrent()
        assertEquals("", viewModel.state.value.name)
        viewModel.onAction(CreateRepositoryAction.NameChanged("bob-draft"))
        sessions.value = alice
        runCurrent()
        assertEquals("alice-private-draft", viewModel.state.value.name)
        assertTrue(viewModel.state.value.isPrivate)
        val recreated = CreateRepositoryViewModel(source, saved, sessions)
        assertEquals("alice-private-draft", recreated.state.value.name)
        sessions.value = GithubSession.SignedOut
        runCurrent()
        viewModel.onAction(CreateRepositoryAction.NameChanged("ignored"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        assertFalse(viewModel.state.value.canSubmit)
        assertEquals("", viewModel.state.value.name)
        assertTrue(source.drafts.isEmpty())
    }

    @Test
    fun lateCreationCannotNavigateOrEraseTheOriginalDraftAfterAccountSwitch() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<GithubRepository>>()
        source.createRequest = { withContext(NonCancellable) { gate.await() } }
        val alice = signedInGithubSession("alice")
        val sessions = MutableStateFlow<GithubSession>(alice)
        val viewModel = CreateRepositoryViewModel(source, SavedStateHandle(), sessions, expectedAccountId = 1)
        viewModel.onAction(CreateRepositoryAction.NameChanged("unfinished"))
        viewModel.onAction(CreateRepositoryAction.Submit)
        runCurrent()
        sessions.value = GithubSession.SignedIn(alice.account.copy(id = 2))
        runCurrent()
        gate.complete(Result.success(CREATED))
        advanceUntilIdle()
        assertNull(viewModel.state.value.created)
        assertFalse(viewModel.state.value.accountAvailable)
        sessions.value = alice
        runCurrent()
        assertEquals("unfinished", viewModel.state.value.name)
        assertNull(viewModel.state.value.created)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    private class FakeRepository : GithubUserRepositoriesRepository {
        var createRequest: (suspend () -> Result<GithubRepository>)? = null
        var result: Result<GithubRepository> = Result.success(CREATED)
        val drafts = mutableListOf<GithubRepositoryCreateDraft>()

        override suspend fun repositories(page: Int, perPage: Int) =
            Result.success(GithubPage<GithubRepository>(emptyList(), null))

        override suspend fun create(draft: GithubRepositoryCreateDraft): Result<GithubRepository> {
            drafts += draft
            return createRequest?.invoke() ?: result
        }
    }

    private companion object {
        val CREATED = GithubRepository(
            21, "demo-app", "joyins/demo-app", null, null, 0, null, true, "https://github.com/joyins/demo-app"
        )
    }
}
