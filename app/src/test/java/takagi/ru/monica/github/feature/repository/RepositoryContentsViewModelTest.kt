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
import takagi.ru.monica.github.data.GithubSignedOutException
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubFileWrite
import takagi.ru.monica.github.domain.GithubFileWriteResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.signedInGithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryContentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun directoryLoadSortsFoldersBeforeFilesAndThenByName() = runTest(dispatcher) {
        val repository = FakeRepository()

        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("alpha", "zeta", "A.kt", "Z.kt"), viewModel.state.value.items.map { it.name })
        assertEquals(listOf("main", "release"), viewModel.state.value.branches.items.map { it.name })
        assertEquals(2, viewModel.state.value.branches.nextPage)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingBranches)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun selectingTagsLoadsThemLazilyAndKeepsBranchPage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        assertEquals(0, repository.tagsCalls)
        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()

        assertEquals(1, repository.tagsCalls)
        assertEquals(listOf("v1.0.0"), viewModel.state.value.tags.items.map { it.name })
        assertEquals(2, viewModel.state.value.tags.nextPage)
        assertEquals(2, viewModel.state.value.branches.nextPage)
    }

    @Test
    fun referencePaginationAppendsWithStableDeduplication() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.LoadMoreBranches)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.branchPages)
        assertEquals(listOf("main", "release", "hotfix"), viewModel.state.value.branches.items.map { it.name })
        assertEquals(null, viewModel.state.value.branches.nextPage)
    }

    @Test
    fun directoryRefreshKeepsItemsAndClearsRefreshingState() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.Refresh)
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isRefreshing)
        assertEquals(4, viewModel.state.value.items.size)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(listOf("alpha", "zeta", "A.kt", "Z.kt"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun failedTagLoadCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository(
            tagsResult = Result.failure(IllegalStateException("offline"))
        )
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tagsError)

        repository.tagsResult = Result.success(GithubPage(listOf(GithubTag("v1.0.0", "tag-sha")), null))
        viewModel.onAction(RepositoryFilesAction.LoadTags)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.tagsError)
        assertEquals("v1.0.0", viewModel.state.value.tags.items.single().name)
    }

    @Test
    fun fileLoadPublishesTypedContentAndSupportsRetry() = runTest(dispatcher) {
        val repository = FakeRepository(fileResult = Result.failure(IllegalStateException("offline")))
        val viewModel = writableFileViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error)

        repository.fileResult = Result.success(GithubFileContent.Text("# README"))
        viewModel.onAction(RepositoryFileAction.Retry)
        advanceUntilIdle()

        assertEquals(GithubFileContent.Text("# README"), viewModel.state.value.content)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun emptyNewFileIsCommittedAndTheDirectoryIsReloaded() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.CreateFile("docs/new.md", "Create docs/new.md", ""))
        advanceUntilIdle()

        val change = repository.writes.single()
        assertEquals("docs/new.md", change.path)
        assertEquals("main", change.branch)
        assertEquals("", change.content)
        assertEquals(null, change.expectedSha)
        assertEquals(
            RepositoryFileMutationOutcome.Succeeded("commit-sha"),
            viewModel.state.value.mutationOutcome
        )
        assertNull(viewModel.state.value.pendingMutation)
        assertEquals(2, repository.directoryCalls)
    }

    @Test
    fun deleteCarriesThePinnedBlobShaAndReportsConflict() = runTest(dispatcher) {
        val repository = FakeRepository(writeResult = Result.failure(GithubApiException(409)))
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.DeleteFile("A.kt", BLOB_SHA, "Delete A.kt"))
        advanceUntilIdle()

        val change = repository.writes.single()
        assertEquals(null, change.content)
        assertEquals(BLOB_SHA, change.expectedSha)
        assertEquals(
            RepositoryFileMutationOutcome.Failed(
                RepositoryFileMutation.Delete("A.kt", "Delete A.kt", BLOB_SHA),
                RepositoryWriteFailure.Conflict
            ),
            viewModel.state.value.mutationOutcome
        )
        assertEquals(1, repository.directoryCalls)
    }

    @Test
    fun commitFailuresAreClassifiedByStatusCode() = runTest(dispatcher) {
        val expectations = listOf(
            GithubApiException(403) to RepositoryWriteFailure.Forbidden,
            GithubApiException(401) to RepositoryWriteFailure.Forbidden,
            GithubApiException(404) to RepositoryWriteFailure.NotFound,
            GithubApiException(422) to RepositoryWriteFailure.InvalidInput,
            GithubSignedOutException() to RepositoryWriteFailure.Forbidden,
            IllegalStateException("offline") to RepositoryWriteFailure.Network
        )
        for ((error, expected) in expectations) {
            val repository = FakeRepository(writeResult = Result.failure(error))
            val viewModel = writableFilesViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(RepositoryFilesAction.CreateFile("new.md", "Create new.md", "hi"))
            advanceUntilIdle()

            val outcome = viewModel.state.value.mutationOutcome
            assertTrue("$error", outcome is RepositoryFileMutationOutcome.Failed)
            assertEquals(expected, (outcome as RepositoryFileMutationOutcome.Failed).failure)
        }
    }

    @Test
    fun invalidPathFailsBeforeAnyCommitIsSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.CreateFile("../escape", "Escape", "x"))
        advanceUntilIdle()

        assertEquals(emptyList<GithubFileWrite>(), repository.writes)
        val outcome = viewModel.state.value.mutationOutcome
        assertTrue(outcome is RepositoryFileMutationOutcome.Failed)
        assertEquals(RepositoryWriteFailure.InvalidInput, (outcome as RepositoryFileMutationOutcome.Failed).failure)
    }

    @Test
    fun aSecondCommitIsIgnoredWhileOneIsInFlight() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.CreateFile("first.md", "Create first.md", "1"))
        viewModel.onAction(RepositoryFilesAction.CreateFile("second.md", "Create second.md", "2"))
        assertEquals(
            RepositoryFileMutation.Create("first.md", "Create first.md", "1"),
            viewModel.state.value.pendingMutation
        )

        advanceUntilIdle()
        assertEquals(listOf("first.md"), repository.writes.map(GithubFileWrite::path))
    }

    @Test
    fun commitsAreOfferedOnlyWhileBrowsingABranch() = runTest(dispatcher) {
        val onBranch = writableFilesViewModel(FakeRepository())
        val onTag = writableFilesViewModel(FakeRepository(), ref = "v1.0.0")
        advanceUntilIdle()

        assertTrue(onBranch.state.value.isOnBranch)
        assertFalse(onTag.state.value.isOnBranch)
    }

    @Test
    fun aReadonlyRoleRefusesEveryCommitBeforeTheRepositoryIsTouched() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = writableFilesViewModel(repository, role = GithubCollaboratorRole.READ)
        advanceUntilIdle()

        viewModel.onAction(RepositoryFilesAction.CreateFile("new.md", "Create new.md", "hi"))
        advanceUntilIdle()
        viewModel.onAction(RepositoryFilesAction.DeleteFile("A.kt", BLOB_SHA, "Delete A.kt"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.writeOutcomeIsForbidden())
        assertEquals(emptyList<GithubFileWrite>(), repository.writes)
        assertEquals(1, repository.directoryCalls)
    }

    @Test
    fun signingOutTakesWriteAccessAwayAgain() = runTest(dispatcher) {
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = RepositoryFilesViewModel("openai", "codex", "main", "", FakeRepository(), details)
            .also { it.onSessionChanged(signedInGithubSession()) }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canWrite)

        viewModel.onSessionChanged(GithubSession.SignedOut)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canWrite)
        assertNull(viewModel.state.value.viewerLogin)
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertEquals(1, details.detailsRequests)
    }

    @Test
    fun theEditorOnlyOpensForAViewerWhoCanPush() = runTest(dispatcher) {
        val readonly = FakeRepository()
        val readonlyViewModel = writableFileViewModel(readonly, role = GithubCollaboratorRole.READ)
        advanceUntilIdle()
        readonlyViewModel.onAction(RepositoryFileAction.StartEdit)
        advanceUntilIdle()

        assertFalse(readonlyViewModel.state.value.editing)
        assertEquals(RepositoryWriteFailure.Forbidden, readonlyViewModel.state.value.writeFailure)

        val writableRepository = FakeRepository()
        val writableViewModel = writableFileViewModel(writableRepository)
        advanceUntilIdle()
        writableViewModel.onAction(RepositoryFileAction.StartEdit)
        advanceUntilIdle()

        assertTrue(writableViewModel.state.value.editing)
        assertEquals(emptyList<GithubFileWrite>(), writableRepository.writes)
    }

    private fun RepositoryFilesUiState.writeOutcomeIsForbidden(): Boolean =
        (mutationOutcome as? RepositoryFileMutationOutcome.Failed)?.failure == RepositoryWriteFailure.Forbidden &&
            pendingMutation == null

    private fun writableFilesViewModel(
        repository: GithubRepositoryContentsRepository,
        ref: String = "main",
        path: String = "",
        role: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
    ) = RepositoryFilesViewModel(
        "openai", "codex", ref, path, repository, TestGithubRepositoryDetailsRepository(role)
    ).also { it.onSessionChanged(signedInGithubSession()) }

    private fun writableFileViewModel(
        repository: GithubRepositoryContentsRepository,
        role: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
    ) = RepositoryFileViewModel(
        "openai", "codex", "main", "README.md", repository, TestGithubRepositoryDetailsRepository(role)
    ).also { it.onSessionChanged(signedInGithubSession()) }

    private class FakeRepository(
        var fileResult: Result<GithubFileContent> = Result.success(GithubFileContent.Text("content")),
        var tagsResult: Result<GithubPage<GithubTag>> = Result.success(
            GithubPage(listOf(GithubTag("v1.0.0", "tag-sha")), nextPage = 2)
        ),
        var writeResult: Result<GithubFileWriteResult> = Result.success(
            GithubFileWriteResult("commit-sha", "blob-sha")
        )
    ) : GithubRepositoryContentsRepository {
        var tagsCalls = 0
        var directoryCalls = 0
        val branchPages = mutableListOf<Int>()
        val writes = mutableListOf<GithubFileWrite>()

        override suspend fun write(
            owner: String,
            name: String,
            change: GithubFileWrite
        ): Result<GithubFileWriteResult> {
            writes += change
            return writeResult
        }

        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubBranch>> {
            branchPages += page
            return Result.success(
                if (page == 1) {
                    GithubPage(
                        items = listOf(
                            GithubBranch("main", "main-sha", isProtected = false),
                            GithubBranch("release", "release-sha", isProtected = true)
                        ),
                        nextPage = 2
                    )
                } else {
                    GithubPage(
                        items = listOf(
                            GithubBranch("release", "release-sha", isProtected = true),
                            GithubBranch("hotfix", "hotfix-sha", isProtected = false)
                        ),
                        nextPage = null
                    )
                }
            )
        }

        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubTag>> {
            tagsCalls += 1
            return tagsResult
        }

        override suspend fun directory(owner: String, name: String, path: String, ref: String?): Result<List<GithubContentItem>> {
            directoryCalls += 1
            return Result.success(
                listOf(
                    item("Z.kt", GithubContentType.FILE),
                    item("zeta", GithubContentType.DIRECTORY),
                    item("A.kt", GithubContentType.FILE),
                    item("alpha", GithubContentType.DIRECTORY)
                )
            )
        }

        override suspend fun file(owner: String, name: String, path: String, ref: String?) = fileResult
    }

    private companion object {
        val BLOB_SHA = "c".repeat(40)

        fun item(name: String, type: GithubContentType) = GithubContentItem(
            name = name,
            path = name,
            sha = "$name-sha",
            size = if (type == GithubContentType.FILE) 10 else 0,
            type = type,
            htmlUrl = null,
            downloadUrl = null
        )
    }
}
