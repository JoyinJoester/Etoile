package takagi.ru.monica.github.feature.repository

import kotlinx.coroutines.CompletableDeferred
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
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.signedInGithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryBranchesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun branchesAppendWithoutDuplicatesAndCanBeFiltered() = runTest(dispatcher) {
        val viewModel = RepositoryBranchesViewModel(
            "openai", "codex", "main", FakeRepository(), TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.LoadMore)
        advanceUntilIdle()
        viewModel.onAction(RepositoryBranchesAction.Search("feature"))

        assertEquals(listOf("main", "feature/android"), viewModel.state.value.items.map(GithubBranch::name))
        assertEquals(listOf("feature/android"), viewModel.state.value.filteredItems.map(GithubBranch::name))
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun createResolvesTheSourceTipAtSubmitTimeThenReloadsTheList() = runTest(dispatcher) {
        val repository = FakeRepository(
            resolveResult = Result.success(LIVE_SHA),
            createdBranch = GithubBranch("feature/new", LIVE_SHA, false)
        )
        val viewModel = writableBranchesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.CreateBranch(" feature/new ", " main "))
        advanceUntilIdle()

        assertEquals(
            listOf("branches:1", "resolve:main", "create:feature/new:$LIVE_SHA", "branches:1"),
            repository.calls
        )
        assertEquals(
            RepositoryBranchWriteOutcome.Succeeded(RepositoryBranchWrite.Create("feature/new", "main")),
            viewModel.state.value.writeOutcome
        )
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun invalidNamesFailWithoutTouchingTheRepository() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableBranchesViewModel(repository)
        advanceUntilIdle()
        repository.calls.clear()

        viewModel.onAction(RepositoryBranchesAction.CreateBranch("bad name", "main"))
        viewModel.onAction(RepositoryBranchesAction.DeleteBranch("feature.lock"))
        viewModel.onAction(RepositoryBranchesAction.RenameBranch("main", "main"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.calls)
        assertTrue(
            viewModel.state.value.writeOutcome is RepositoryBranchWriteOutcome.Failed
        )
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun unresolvableSourceAbortsBeforeTheBranchIsCreated() = runTest(dispatcher) {
        val repository = FakeRepository(resolveResult = Result.failure(IllegalStateException("gone")))
        val viewModel = writableBranchesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.CreateBranch("feature/new", "deleted-branch"))
        advanceUntilIdle()

        assertEquals(
            listOf("branches:1", "resolve:deleted-branch"),
            repository.calls
        )
        assertTrue(viewModel.state.value.writeOutcome is RepositoryBranchWriteOutcome.Failed)
    }

    @Test
    fun aWriteAlreadyInFlightIgnoresFurtherWritesUntilItSettles() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(deleteGate = gate)
        val viewModel = writableBranchesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.DeleteBranch("feature/android"))
        advanceUntilIdle()
        viewModel.onAction(RepositoryBranchesAction.DeleteBranch("release"))
        advanceUntilIdle()

        assertEquals(RepositoryBranchWrite.Delete("feature/android"), viewModel.state.value.pendingWrite)
        assertEquals(listOf("branches:1", "delete:feature/android"), repository.calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun dismissingTheOutcomeClearsItSoTheErrorCannotReappear() = runTest(dispatcher) {
        val repository = FakeRepository(deleteResult = Result.failure(IllegalStateException("protected")))
        val viewModel = writableBranchesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.DeleteBranch("release"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.writeOutcome is RepositoryBranchWriteOutcome.Failed)

        viewModel.onAction(RepositoryBranchesAction.DismissWriteOutcome)
        assertNull(viewModel.state.value.writeOutcome)
    }

    private fun writableBranchesViewModel(
        repository: FakeRepository,
        role: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
    ) = RepositoryBranchesViewModel(
        "openai", "codex", "main", repository, TestGithubRepositoryDetailsRepository(role)
    ).also { it.onSessionChanged(signedInGithubSession()) }

    @Test
    fun aReadonlyRoleIsRefusedBeforeTheRepositoryIsTouched() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableBranchesViewModel(repository, GithubCollaboratorRole.READ)
        advanceUntilIdle()
        repository.calls.clear()

        viewModel.onAction(RepositoryBranchesAction.DeleteBranch("feature/android"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canWrite)
        assertEquals(
            RepositoryBranchWriteOutcome.Failed(
                RepositoryBranchWrite.Delete("feature/android"),
                RepositoryWriteFailure.Forbidden
            ),
            viewModel.state.value.writeOutcome
        )

        viewModel.onAction(RepositoryBranchesAction.RenameBranch("feature/android", "feature/renamed"))
        advanceUntilIdle()

        assertEquals(
            RepositoryBranchWriteOutcome.Failed(
                RepositoryBranchWrite.Rename("feature/android", "feature/renamed"),
                RepositoryWriteFailure.Forbidden
            ),
            viewModel.state.value.writeOutcome
        )
        assertEquals(emptyList<String>(), repository.calls)
    }

    @Test
    fun signingOutTakesWriteAccessAwayAgain() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = RepositoryBranchesViewModel("openai", "codex", "main", FakeRepository(), details)
        viewModel.onSessionChanged(signedInGithubSession())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canWrite)

        viewModel.onSessionChanged(GithubSession.SignedOut)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canWrite)
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertEquals(1, details.detailsRequests)
    }

    @Test
    fun eachRefusalReasonGetsItsOwnExplanation() = runTest(dispatcher) {
        listOf(
            GithubApiException(403) to RepositoryWriteFailure.Forbidden,
            GithubApiException(403, rateLimited = true) to RepositoryWriteFailure.RateLimited,
            GithubApiException(409) to RepositoryWriteFailure.Conflict,
            GithubApiException(422) to RepositoryWriteFailure.InvalidInput,
            GithubApiException(429) to RepositoryWriteFailure.RateLimited
        ).forEach { (error, expected) ->
            val viewModel = writableBranchesViewModel(FakeRepository(deleteResult = Result.failure(error)))
            advanceUntilIdle()

            viewModel.onAction(RepositoryBranchesAction.DeleteBranch("feature/android"))
            advanceUntilIdle()

            assertEquals(
                expected,
                (viewModel.state.value.writeOutcome as RepositoryBranchWriteOutcome.Failed).failure
            )
        }
    }

    @Test
    fun aProtectedBranchRenameReportsTheConflictItWasRefusedWith() = runTest(dispatcher) {
        val viewModel = writableBranchesViewModel(
            FakeRepository(renameResult = Result.failure(GithubApiException(409)))
        )
        advanceUntilIdle()

        viewModel.onAction(RepositoryBranchesAction.RenameBranch("feature/android", "feature/mobile"))
        advanceUntilIdle()

        assertEquals(
            RepositoryBranchWriteOutcome.Failed(
                RepositoryBranchWrite.Rename("feature/android", "feature/mobile"),
                RepositoryWriteFailure.Conflict
            ),
            viewModel.state.value.writeOutcome
        )
    }

    private class FakeRepository(
        private val resolveResult: Result<String> = Result.success(LIVE_SHA),
        private val createdBranch: GithubBranch = GithubBranch("feature/new", LIVE_SHA, false),
        private val deleteResult: Result<Unit> = Result.success(Unit),
        private val renameResult: Result<GithubBranch> = Result.success(GithubBranch("renamed", LIVE_SHA, false)),
        private val deleteGate: CompletableDeferred<Unit>? = null
    ) : GithubRepositoryContentsRepository {
        val calls = mutableListOf<String>()

        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubBranch>> {
            calls += "branches:$page"
            return when (page) {
                1 -> Result.success(
                    GithubPage(
                        listOf(GithubBranch("main", "abc", true)),
                        nextPage = 2
                    )
                )
                else -> Result.success(
                    GithubPage(
                        listOf(
                            GithubBranch("main", "abc", true),
                            GithubBranch("feature/android", "def", false)
                        ),
                        nextPage = null
                    )
                )
            }
        }

        override suspend fun resolveRef(owner: String, name: String, ref: String): Result<String> {
            calls += "resolve:$ref"
            return resolveResult
        }

        override suspend fun createBranch(
            owner: String,
            name: String,
            branch: String,
            fromSha: String
        ): Result<GithubBranch> {
            calls += "create:$branch:$fromSha"
            return Result.success(createdBranch)
        }

        override suspend fun deleteBranch(owner: String, name: String, branch: String): Result<Unit> {
            calls += "delete:$branch"
            deleteGate?.await()
            return deleteResult
        }

        override suspend fun renameBranch(
            owner: String,
            name: String,
            branch: String,
            newName: String
        ): Result<GithubBranch> {
            calls += "rename:$branch:$newName"
            return renameResult
        }

        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubTag>(emptyList(), null))

        override suspend fun directory(owner: String, name: String, path: String, ref: String?) =
            Result.success(emptyList<GithubContentItem>())

        override suspend fun file(owner: String, name: String, path: String, ref: String?) =
            Result.success<GithubFileContent>(GithubFileContent.Text(""))
    }

    private companion object {
        val LIVE_SHA = "b".repeat(40)
    }
}
