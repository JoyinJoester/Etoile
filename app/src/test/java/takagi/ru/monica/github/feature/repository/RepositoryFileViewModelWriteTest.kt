package takagi.ru.monica.github.feature.repository

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.data.GithubApiException

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryFileViewModelWriteTest {
    @After fun reset() = Dispatchers.resetMain()

    @Test fun saveUsesLoadedShaAndKeepsDraftOnConflict() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo().apply { writeResult = Result.failure(GithubApiException(409)) }
        val vm = writable(repo)
        advanceUntilIdle()
        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("changed"))
        vm.onAction(RepositoryFileAction.Save); advanceUntilIdle()
        assertEquals("a".repeat(40), repo.changes.single().expectedSha)
        assertEquals(RepositoryWriteFailure.Conflict, vm.state.value.writeFailure)
        assertTrue(vm.state.value.editing)
        assertEquals("changed", vm.state.value.draftText)
    }

    @Test fun successfulSaveUpdatesContentAndCommit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val vm = writable(repo)
        advanceUntilIdle(); vm.onAction(RepositoryFileAction.StartEdit); vm.onAction(RepositoryFileAction.DraftChanged("changed")); vm.onAction(RepositoryFileAction.Save); advanceUntilIdle()
        assertEquals("changed", (vm.state.value.content as GithubFileContent.Text).value)
        assertEquals("commit-sha", vm.state.value.writtenCommit)
        assertFalse(vm.state.value.editing)
    }

    @Test fun dismissingTheCommitReceiptClearsItSoTheNoticeCannotReplay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = writable(FakeRepo())
        advanceUntilIdle(); vm.onAction(RepositoryFileAction.StartEdit); vm.onAction(RepositoryFileAction.DraftChanged("changed")); vm.onAction(RepositoryFileAction.Save); advanceUntilIdle()
        assertEquals("commit-sha", vm.state.value.writtenCommit)
        vm.onAction(RepositoryFileAction.DismissSaved); advanceUntilIdle()
        assertNull(vm.state.value.writtenCommit)
    }

    @Test fun openingAnotherEditDropsTheStaleCommitReceipt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = writable(FakeRepo())
        advanceUntilIdle(); vm.onAction(RepositoryFileAction.StartEdit); vm.onAction(RepositoryFileAction.DraftChanged("changed")); vm.onAction(RepositoryFileAction.Save); advanceUntilIdle()
        vm.onAction(RepositoryFileAction.StartEdit)
        assertNull(vm.state.value.writtenCommit)
        assertNull(vm.state.value.writeFailure)
    }

    @Test fun consecutiveSavesCarryTheShaReturnedByThePreviousWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val vm = writable(repo)
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("first"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()
        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("second"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()

        assertEquals("a".repeat(40), repo.changes[0].expectedSha)
        assertEquals("b".repeat(40), repo.changes[1].expectedSha)
        assertEquals("second", (vm.state.value.content as GithubFileContent.Text).value)
        assertNull(vm.state.value.writeFailure)
    }

    @Test fun saveFallsBackToThePreviousShaWhenTheApiOmitsContentSha() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo().apply { writeResult = Result.success(GithubFileWriteResult("commit-sha", null)) }
        val vm = writable(repo)
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("first"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()
        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("second"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()

        assertEquals("a".repeat(40), repo.changes[1].expectedSha)
    }

    @Test fun rejectedWriteIsReportedInsteadOfSilentlyIgnoringTheTap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo().apply {
            fileResult = Result.success(GithubFileContent.Text("original", "not-a-blob-sha"))
        }
        val vm = writable(repo)
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("changed"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.InvalidInput, vm.state.value.writeFailure)
        assertTrue(vm.state.value.editing)
        assertEquals("changed", vm.state.value.draftText)
        assertTrue(repo.changes.isEmpty())
    }

    @Test fun rejectedByPermissionIsReportedAsForbidden() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo().apply { writeResult = Result.failure(GithubApiException(403)) }
        val vm = writable(repo)
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.DraftChanged("changed"))
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Forbidden, vm.state.value.writeFailure)
        assertEquals("changed", vm.state.value.draftText)
        assertNull(vm.state.value.writtenCommit)
    }

    @Test fun aReadonlyViewerCannotEvenOpenTheEditor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val vm = RepositoryFileViewModel(
            "o", "r", "main", "file.txt", repo, TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.READ)
        ).also { it.onSessionChanged(signedInGithubSession()) }
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        advanceUntilIdle()

        assertFalse(vm.state.value.editing)
        assertEquals(RepositoryWriteFailure.Forbidden, vm.state.value.writeFailure)
        assertTrue(repo.changes.isEmpty())
    }

    @Test fun losingPushAccessMidPageClosesTheEditorGate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val vm = writable(repo)
        advanceUntilIdle()
        vm.onSessionChanged(GithubSession.SignedOut)
        advanceUntilIdle()

        vm.onAction(RepositoryFileAction.StartEdit)
        vm.onAction(RepositoryFileAction.Save)
        advanceUntilIdle()

        assertFalse(vm.state.value.editing)
        assertEquals(RepositoryWriteFailure.Forbidden, vm.state.value.writeFailure)
        assertTrue(repo.changes.isEmpty())
    }

    private fun writable(
        repo: GithubRepositoryContentsRepository,
        role: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
    ) = RepositoryFileViewModel(
        "o", "r", "main", "file.txt", repo, TestGithubRepositoryDetailsRepository(role)
    ).also { it.onSessionChanged(signedInGithubSession()) }

    private class FakeRepo
 : GithubRepositoryContentsRepository {
        val changes = mutableListOf<GithubFileWrite>()
        var fileResult: Result<GithubFileContent> = Result.success(GithubFileContent.Text("original", "a".repeat(40)))
        var writeResult: Result<GithubFileWriteResult> = Result.success(GithubFileWriteResult("commit-sha", "b".repeat(40)))
        override suspend fun file(owner: String, name: String, path: String, ref: String?) = fileResult
        override suspend fun write(owner: String, name: String, change: GithubFileWrite): Result<GithubFileWriteResult> {
            changes += change
            return writeResult
        }
        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubBranch>(emptyList(), null))
        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubTag>(emptyList(), null))
        override suspend fun directory(owner: String, name: String, path: String, ref: String?) = Result.success(emptyList<GithubContentItem>())
    }
}
