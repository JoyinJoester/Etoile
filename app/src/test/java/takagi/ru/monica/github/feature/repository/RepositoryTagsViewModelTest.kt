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
class RepositoryTagsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tagsAppendWithoutDuplicatesAndCanBeFiltered() = runTest(dispatcher) {
        val viewModel = RepositoryTagsViewModel(
            "openai", "codex", "main", FakeRepository(), TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()

        viewModel.onAction(RepositoryTagsAction.LoadMore)
        advanceUntilIdle()
        viewModel.onAction(RepositoryTagsAction.Search("1.2"))

        assertEquals(listOf("v1.1.0", "v1.2.0"), viewModel.state.value.items.map(GithubTag::name))
        assertEquals(listOf("v1.2.0"), viewModel.state.value.filteredItems.map(GithubTag::name))
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun createResolvesTheSourceTipAtSubmitTimeThenReloadsTheList() = runTest(dispatcher) {
        val repository = FakeRepository(
            resolveResult = Result.success(LIVE_SHA),
            createdTag = GithubTag("v1.2.0", LIVE_SHA)
        )
        val viewModel = writableTagsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryTagsAction.CreateTag(" v1.2.0 ", " release "))
        advanceUntilIdle()

        assertEquals(
            listOf("tags:1", "resolve:release", "create:v1.2.0:$LIVE_SHA", "tags:1"),
            repository.calls
        )
        assertEquals(
            RepositoryTagWriteOutcome.Succeeded(RepositoryTagWrite.Create("v1.2.0", "release")),
            viewModel.state.value.writeOutcome
        )
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun invalidNamesFailWithoutTouchingTheRepository() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableTagsViewModel(repository)
        advanceUntilIdle()
        repository.calls.clear()

        viewModel.onAction(RepositoryTagsAction.CreateTag("bad name", "main"))
        viewModel.onAction(RepositoryTagsAction.DeleteTag("release.lock"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.calls)
        assertTrue(viewModel.state.value.writeOutcome is RepositoryTagWriteOutcome.Failed)
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun unresolvableSourceAbortsBeforeTheTagIsCreated() = runTest(dispatcher) {
        val repository = FakeRepository(resolveResult = Result.failure(IllegalStateException("gone")))
        val viewModel = writableTagsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryTagsAction.CreateTag("v1.2.0", "deleted-branch"))
        advanceUntilIdle()

        assertEquals(listOf("tags:1", "resolve:deleted-branch"), repository.calls)
        assertTrue(viewModel.state.value.writeOutcome is RepositoryTagWriteOutcome.Failed)
    }

    @Test
    fun aWriteAlreadyInFlightIgnoresFurtherWritesUntilItSettles() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(deleteGate = gate)
        val viewModel = writableTagsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryTagsAction.DeleteTag("v1.1.0"))
        advanceUntilIdle()
        viewModel.onAction(RepositoryTagsAction.DeleteTag("v1.2.0"))
        advanceUntilIdle()

        assertEquals(RepositoryTagWrite.Delete("v1.1.0"), viewModel.state.value.pendingWrite)
        assertEquals(listOf("tags:1", "delete:v1.1.0"), repository.calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.state.value.pendingWrite)
    }

    @Test
    fun dismissingTheOutcomeClearsItSoTheErrorCannotReappear() = runTest(dispatcher) {
        val repository = FakeRepository(deleteResult = Result.failure(IllegalStateException("protected")))
        val viewModel = writableTagsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryTagsAction.DeleteTag("v1.1.0"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.writeOutcome is RepositoryTagWriteOutcome.Failed)

        viewModel.onAction(RepositoryTagsAction.DismissWriteOutcome)
        assertNull(viewModel.state.value.writeOutcome)
    }

    private fun writableTagsViewModel(
        repository: FakeRepository,
        role: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
    ) = RepositoryTagsViewModel(
        "openai", "codex", "main", repository, TestGithubRepositoryDetailsRepository(role)
    ).also { it.onSessionChanged(signedInGithubSession()) }

    @Test
    fun aReadonlyRoleIsRefusedBeforeTheRepositoryIsTouched() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableTagsViewModel(repository, GithubCollaboratorRole.READ)
        advanceUntilIdle()
        repository.calls.clear()

        viewModel.onAction(RepositoryTagsAction.DeleteTag("v1.1.0"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canWrite)
        assertEquals(emptyList<String>(), repository.calls)
        assertEquals(
            RepositoryTagWriteOutcome.Failed(
                RepositoryTagWrite.Delete("v1.1.0"),
                RepositoryWriteFailure.Forbidden
            ),
            viewModel.state.value.writeOutcome
        )
    }

    @Test
    fun signingOutTakesWriteAccessAwayAgain() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = RepositoryTagsViewModel("openai", "codex", "main", FakeRepository(), details)
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
            GithubApiException(404) to RepositoryWriteFailure.NotFound,
            GithubApiException(422) to RepositoryWriteFailure.InvalidInput,
            GithubApiException(429) to RepositoryWriteFailure.RateLimited,
            IllegalStateException("offline") to RepositoryWriteFailure.Network
        ).forEach { (error, expected) ->
            val viewModel = writableTagsViewModel(FakeRepository(deleteResult = Result.failure(error)))
            advanceUntilIdle()

            viewModel.onAction(RepositoryTagsAction.DeleteTag("v1.1.0"))
            advanceUntilIdle()

            val outcome = viewModel.state.value.writeOutcome
            assertEquals(
                expected,
                (outcome as RepositoryTagWriteOutcome.Failed).failure
            )
        }
    }

    private class FakeRepository(
        private val resolveResult: Result<String> = Result.success(LIVE_SHA),
        private val createdTag: GithubTag = GithubTag("v1.2.0", LIVE_SHA),
        private val deleteResult: Result<Unit> = Result.success(Unit),
        private val deleteGate: CompletableDeferred<Unit>? = null
    ) : GithubRepositoryContentsRepository {
        val calls = mutableListOf<String>()

        override suspend fun tags(
            owner: String,
            name: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubTag>> {
            calls += "tags:$page"
            return when (page) {
                1 -> Result.success(GithubPage(listOf(GithubTag("v1.1.0", "abc")), nextPage = 2))
                else -> Result.success(
                    GithubPage(
                        listOf(GithubTag("v1.1.0", "abc"), GithubTag("v1.2.0", "def")),
                        nextPage = null
                    )
                )
            }
        }

        override suspend fun resolveRef(owner: String, name: String, ref: String): Result<String> {
            calls += "resolve:$ref"
            return resolveResult
        }

        override suspend fun createTag(
            owner: String,
            name: String,
            tag: String,
            fromSha: String
        ): Result<GithubTag> {
            calls += "create:$tag:$fromSha"
            return Result.success(createdTag)
        }

        override suspend fun deleteTag(owner: String, name: String, tag: String): Result<Unit> {
            calls += "delete:$tag"
            deleteGate?.await()
            return deleteResult
        }

        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubBranch>(emptyList(), null))

        override suspend fun directory(owner: String, name: String, path: String, ref: String?) =
            Result.success(emptyList<GithubContentItem>())

        override suspend fun file(owner: String, name: String, path: String, ref: String?) =
            Result.success<GithubFileContent>(GithubFileContent.Text(""))
    }

    private companion object {
        val LIVE_SHA = "b".repeat(40)
    }
}
