package takagi.ru.monica.github.feature.releases

import androidx.lifecycle.SavedStateHandle

import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubAccount
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.Continuation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.MutableStateFlow
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
import takagi.ru.monica.github.data.InMemoryGithubActionJournal
import takagi.ru.monica.github.domain.GithubActionKind
import takagi.ru.monica.github.domain.GithubActionOperation
import takagi.ru.monica.github.domain.GithubAssetInput
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.domain.GithubReleaseDraft
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.GithubReleaseAssetUpload
import takagi.ru.monica.github.domain.GithubUserSummary

@OptIn(ExperimentalCoroutinesApi::class)
class ReleasesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listLoadsAndAppendsReleasePages() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubRelease::id))
        assertEquals(listOf(1, 2), repository.pages)
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun nextPageFailureKeepsLoadedReleasesAndCanRetry() = runTest(dispatcher) {
        val repository = FakeReleasesRepository().apply { failSecondPage = true }
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.state.value.items.map(GithubRelease::id))
        assertTrue(viewModel.state.value.error)

        repository.failSecondPage = false
        viewModel.onAction(ReleasesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.items.map(GithubRelease::id))
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun refreshKeepsExistingReleasesAndUsesRefreshRepositoryPath() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.Refresh)
        assertTrue(viewModel.state.value.isRefreshing)
        advanceUntilIdle()

        assertEquals(listOf(1), repository.refreshRequests)
        assertEquals(1, viewModel.state.value.items.size)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun retryAfterFailedRefreshReplaysFirstPagePastTheCache() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        repository.failRefresh = true
        viewModel.onAction(ReleasesAction.Refresh)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error)
        assertEquals(listOf(1L), viewModel.state.value.items.map(GithubRelease::id))
        assertEquals(listOf(1), repository.refreshRequests)

        repository.failRefresh = false
        viewModel.onAction(ReleasesAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.refreshRequests)
        assertEquals(listOf(1, 1), repository.pages)
        assertFalse(viewModel.state.value.error)
        assertEquals(listOf(1L), viewModel.state.value.items.map(GithubRelease::id))
    }

    @Test
    fun detailLoadsReleaseIndependently() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleaseDetailViewModel(
            owner = "openai",
            name = "codex",
            reference = ReleaseReference.Id(7),
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(7L, viewModel.state.value.release?.id)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun tagDetailUsesTheTagEndpoint() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleaseDetailViewModel(
            owner = "openai",
            name = "codex",
            reference = ReleaseReference.Tag("preview/1.2"),
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(listOf("preview/1.2"), repository.tags)
        assertEquals("Release 12", viewModel.state.value.release?.name)
    }

    @Test
    fun removingAnAttachmentReloadsTheRelease() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = detailViewModel(repository, ReleaseReference.Id(7))
        advanceUntilIdle()

        viewModel.onAction(ReleaseDetailAction.RemoveAsset(70))
        assertTrue(viewModel.state.value.isRemovingAsset)
        assertEquals(70L, viewModel.state.value.removingAssetId)
        advanceUntilIdle()

        assertEquals(listOf(70L), repository.removedAssets)
        assertEquals(listOf(7L, 7L), repository.detailLoads)
        assertNull(viewModel.state.value.removingAssetId)
        assertFalse(viewModel.state.value.assetRemovalFailed)
    }

    @Test
    fun aRejectedAttachmentRemovalKeepsTheRowAndSaysSo() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = detailViewModel(repository, ReleaseReference.Id(7))
        advanceUntilIdle()
        repository.writeFailure = GithubApiException(403)

        viewModel.onAction(ReleaseDetailAction.RemoveAsset(70))
        advanceUntilIdle()

        assertEquals(listOf(70L), repository.removedAssets)
        assertTrue(viewModel.state.value.assetRemovalFailed)
        assertNull(viewModel.state.value.removingAssetId)
        assertEquals(listOf(7L), repository.detailLoads)
    }

    @Test
    fun createReleaseNormalisesInputThenReloadsPastTheCache() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(
            ReleasesAction.Save(
                releaseId = null,
                tagName = "  v1.3.0  ",
                title = "  Etoile 1.3 ",
                body = "Notes",
                targetCommitish = "main",
                isDraft = true,
                isPrerelease = false
            )
        )
        assertTrue(viewModel.state.value.isMutating)
        advanceUntilIdle()

        val draft = repository.created.single()
        assertEquals("v1.3.0", draft.tagName)
        assertEquals("Etoile 1.3", draft.title)
        assertEquals("main", draft.targetCommitish)
        assertTrue(draft.isDraft)
        assertEquals(null, viewModel.state.value.pendingMutation)
        assertEquals(
            ReleaseMutationOutcome.Succeeded(ReleaseMutationKind.Create),
            viewModel.state.value.mutationOutcome
        )
        assertEquals(listOf(1), repository.refreshRequests)
    }

    @Test
    fun secondWriteIsIgnoredWhileOneIsInFlight() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.Save(null, "v1.0.0", "One", "", "main", false, false))
        viewModel.onAction(ReleasesAction.Save(null, "v2.0.0", "Two", "", "main", false, false))
        advanceUntilIdle()

        assertEquals(listOf("v1.0.0"), repository.created.map(GithubReleaseDraft::tagName))
    }

    @Test
    fun invalidTagNameNeverReachesTheRepository() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.Save(null, "v1..0", "Title", "", "main", false, false))
        advanceUntilIdle()

        assertTrue(repository.created.isEmpty())
        assertEquals(
            ReleaseMutationOutcome.Failed(ReleaseMutationKind.Create, ReleaseMutationFailure.InvalidInput),
            viewModel.state.value.mutationOutcome
        )
    }

    @Test
    fun publishingADraftKeepsTheTargetOfTheReleaseItEdits() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(
            ReleasesAction.Save(
                releaseId = 1,
                tagName = "v1.0.0",
                title = "Release 1",
                body = "Highlights",
                targetCommitish = "release/1.x",
                isDraft = false,
                isPrerelease = true
            )
        )
        advanceUntilIdle()

        val (releaseId, draft) = repository.updated.single()
        assertEquals(1L, releaseId)
        assertEquals("release/1.x", draft.targetCommitish)
        assertFalse(draft.isDraft)
        assertTrue(draft.isPrerelease)
    }

    @Test
    fun deleteRemovesTheReleaseAndReloadsTheList() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(ReleasesAction.Delete(42))
        advanceUntilIdle()

        assertEquals(listOf(42L), repository.deleted)
        assertEquals(
            ReleaseMutationOutcome.Succeeded(ReleaseMutationKind.Delete),
            viewModel.state.value.mutationOutcome
        )
        assertEquals(listOf(1), repository.refreshRequests)
    }

    @Test
    fun writeFailuresAreClassifiedForTheEditor() = runTest(dispatcher) {
        val cases = listOf(
            GithubApiException(401) to ReleaseMutationFailure.Forbidden,
            GithubApiException(403) to ReleaseMutationFailure.Forbidden,
            GithubApiException(403, rateLimited = true) to ReleaseMutationFailure.RateLimited,
            GithubApiException(429) to ReleaseMutationFailure.RateLimited,
            GithubSignedOutException() to ReleaseMutationFailure.Forbidden,
            GithubApiException(404) to ReleaseMutationFailure.NotFound,
            GithubApiException(409) to ReleaseMutationFailure.Conflict,
            GithubApiException(422) to ReleaseMutationFailure.InvalidInput,
            IllegalStateException("offline") to ReleaseMutationFailure.Network
        )
        cases.forEach { (error, expected) ->
            val repository = FakeReleasesRepository().apply { writeFailure = error }
            val viewModel = ReleasesViewModel("openai", "codex", repository)
            advanceUntilIdle()

            viewModel.onAction(ReleasesAction.Delete(42))
            advanceUntilIdle()

            val outcome = viewModel.state.value.mutationOutcome
            assertEquals(
                ReleaseMutationOutcome.Failed(ReleaseMutationKind.Delete, expected),
                outcome
            )
            assertEquals(null, viewModel.state.value.pendingMutation)
        }
    }

    @Test
    fun attachUploadsThePickedFileThenReloadsTheList() = runTest(dispatcher) {
        val repository = FakeReleasesRepository()
        val viewModel = ReleasesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(attachAction(releaseId = 7, fileName = " Etoile-arm64.apk "))
        assertTrue(viewModel.state.value.isMutating)
        advanceUntilIdle()

        val (releaseId, asset) = repository.uploaded.single()
        assertEquals(7L, releaseId)
        assertEquals("Etoile-arm64.apk", asset.fileName)
        assertEquals(4_096L, asset.contentLength)
        assertEquals(
            ReleaseMutationOutcome.Succeeded(ReleaseMutationKind.Attach),
            viewModel.state.value.mutationOutcome
        )
        assertEquals(listOf(1), repository.refreshRequests)
        assertFalse(viewModel.state.value.isMutating)
    }

    @Test
    fun anUnusablePickNeverStartsAnUpload() = runTest(dispatcher) {
        listOf(
            "Etoile.apk" to 0L,
            "2026/09/20 log.txt" to 4_096L,
            "Etoile.apk" to GithubReleaseAssetUpload.MAX_CONTENT_LENGTH + 1
        ).forEach { (fileName, size) ->
            val repository = FakeReleasesRepository()
            val viewModel = ReleasesViewModel("openai", "codex", repository)
            advanceUntilIdle()

            viewModel.onAction(attachAction(releaseId = 7, fileName = fileName, contentLength = size))
            advanceUntilIdle()

            assertEquals(emptyList<Pair<Long, GithubReleaseAssetUpload>>(), repository.uploaded)
            assertEquals(
                ReleaseMutationOutcome.Failed(ReleaseMutationKind.Attach, ReleaseMutationFailure.InvalidInput),
                viewModel.state.value.mutationOutcome
            )
        }
    }

    private fun detailViewModel(
        repository: FakeReleasesRepository,
        reference: ReleaseReference
    ) = ReleaseDetailViewModel(
        owner = "openai",
        name = "codex",
        reference = reference,
        repository = repository
    )

    private fun attachAction(
        releaseId: Long,
        fileName: String,
        contentLength: Long = 4_096L
    ) = ReleasesAction.Attach(
        releaseId = releaseId,
        fileName = fileName,
        label = "",
        contentType = "application/vnd.android.package-archive",
        contentLength = contentLength,
        // The ViewModel validates and forwards the pick without ever reading its bytes.
        open = { EmptyAssetInput }
    )

    private object EmptyAssetInput : GithubAssetInput {
        override fun read(target: ByteArray, offset: Int, count: Int) = -1

        override fun close() = Unit
    }

    @Test
    fun releaseReadAndDeleteCompletionCannotLeakAcrossAccountsWithoutAnActiveScreen() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val base = FakeReleasesRepository()
        lateinit var read: Continuation<Result<GithubRelease>>
        lateinit var write: Continuation<Result<Unit>>
        var delayed = false
        val repository = object : GithubReleasesRepository by base {
            override fun forAccount(accountId: Long?): GithubReleasesRepository = this
            override suspend fun release(owner: String, name: String, releaseId: Long): Result<GithubRelease> =
                if (!delayed) base.release(owner, name, releaseId)
                else if ((sessions.value as? GithubSession.SignedIn)?.account?.id == 1L) suspendCoroutine { read = it }
                else Result.failure(IllegalStateException("private release denied"))
            override suspend fun deleteAsset(owner: String, name: String, assetId: Long): Result<Unit> = suspendCoroutine { write = it }
        }
        val vm = ReleaseDetailViewModel("o", "r", ReleaseReference.Id(1), repository,
            sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        assertTrue(vm.state.value.canManage)
        vm.onAction(ReleaseDetailAction.RemoveAsset(10, 1L))
        runCurrent()
        delayed = true
        vm.onAction(ReleaseDetailAction.Retry)
        runCurrent()
        sessions.value = first.copy(account = first.account.copy(id = 2))
        runCurrent()
        read.resume(base.release("o", "r", 1))
        write.resume(Result.success(Unit))
        runCurrent()
        assertEquals(2L, vm.state.value.viewerAccountId)
        assertNull(vm.state.value.release)
        assertNull(vm.state.value.removingAssetId)
    }

    @Test fun editorDraftsRestoreByNumericAccountAndTargetAfterStateRecreation() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val saved = SavedStateHandle()
        val repo = FakeReleasesRepository()
        val details = TestGithubRepositoryDetailsRepository()
        val vm = ReleasesViewModel("o", "r", repo, sessions, details, saved)
        runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        val draftA = vm.state.value.editor!!.copy(tagName = "v3", body = "private A", isDraft = true)
        vm.onAction(ReleasesAction.EditEditor(draftA))
        vm.onAction(ReleasesAction.CloseEditor)
        vm.onAction(ReleasesAction.OpenEditor(1))
        vm.onAction(ReleasesAction.EditEditor(vm.state.value.editor!!.copy(body = "edit target 1")))
        sessions.value = first.copy(account = first.account.copy(id = 2)); runCurrent()
        assertNull(vm.state.value.editor)
        vm.onAction(ReleasesAction.OpenEditor())
        assertEquals("", vm.state.value.editor?.body)
        vm.onAction(ReleasesAction.EditEditor(draftA)) // Stale field callback from A.
        assertEquals("", vm.state.value.editor?.body)
        vm.onAction(ReleasesAction.EditEditor(vm.state.value.editor!!.copy(body = "private B")))

        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        sessions.value = first
        val recreated = ReleasesViewModel("o", "r", repo, sessions, details, restored)
        runCurrent()
        assertNull(recreated.state.value.editor) // Restoration never reopens a confirmation/editor.
        recreated.onAction(ReleasesAction.OpenEditor())
        assertEquals(draftA, recreated.state.value.editor)
        recreated.onAction(ReleasesAction.OpenEditor(1))
        assertEquals("edit target 1", recreated.state.value.editor?.body)
        sessions.value = first.copy(account = first.account.copy(id = 2)); runCurrent()
        recreated.onAction(ReleasesAction.OpenEditor())
        assertEquals("private B", recreated.state.value.editor?.body)
    }

    @Test fun oldCreateCompletionCannotEraseEitherAccountsDraftOrAcceptStaleConsent() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val base = FakeReleasesRepository()
        lateinit var completion: Continuation<Result<GithubRelease>>
        var calls = 0
        val repo = object : GithubReleasesRepository by base {
            override fun forAccount(accountId: Long?): GithubReleasesRepository = this
            override suspend fun createRelease(owner: String, name: String, draft: GithubReleaseDraft): Result<GithubRelease> {
                calls++
                return suspendCoroutine { completion = it }
            }
        }
        val vm = ReleasesViewModel("o", "r", repo, sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        val draftA = vm.state.value.editor!!.copy(tagName = "v3", body = "private A")
        vm.onAction(ReleasesAction.EditEditor(draftA))
        vm.onAction(saveEditor(draftA)); runCurrent()
        sessions.value = first.copy(account = first.account.copy(id = 2)); runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        val draftB = vm.state.value.editor!!.copy(tagName = "v4", body = "private B")
        vm.onAction(ReleasesAction.EditEditor(draftB))
        vm.onAction(saveEditor(draftA))
        assertEquals(1, calls)
        completion.resume(base.release("o", "r", 1)); runCurrent()
        assertEquals(draftB, vm.state.value.editor)
        assertNull(vm.state.value.mutationOutcome)
        sessions.value = first; runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        assertEquals(draftA, vm.state.value.editor)
    }

    @Test fun failedSaveRetainsEditorAndSuccessfulSaveClearsOnlyItsTargetDraft() = runTest(dispatcher) {
        val repo = FakeReleasesRepository()
        val vm = ReleasesViewModel("o", "r", repo)
        runCurrent()
        vm.onAction(ReleasesAction.OpenEditor(1))
        val edit = vm.state.value.editor!!.copy(body = "keep edit")
        vm.onAction(ReleasesAction.EditEditor(edit))
        vm.onAction(ReleasesAction.OpenEditor())
        val create = vm.state.value.editor!!.copy(tagName = "v3", body = "retry")
        vm.onAction(ReleasesAction.EditEditor(create))
        repo.writeFailure = IllegalStateException("offline")
        vm.onAction(saveEditor(create)); runCurrent()
        assertEquals(create, vm.state.value.editor)
        repo.writeFailure = null
        vm.onAction(saveEditor(create).copy(repeatConfirmed = true)); runCurrent()
        assertNull(vm.state.value.editor)
        vm.onAction(ReleasesAction.OpenEditor())
        assertEquals("", vm.state.value.editor?.body)
        vm.onAction(ReleasesAction.OpenEditor(1, asDraft = false))
        assertEquals("keep edit", vm.state.value.editor?.body)
        assertEquals(false, vm.state.value.editor?.isDraft)
        assertTrue(repo.updated.isEmpty()) // Publish menu opens an editor; only Save writes.
    }

    @Test fun liveAccountChangeRejectsSaveBeforeCollectorRuns() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val repository = FakeReleasesRepository()
        val vm = ReleasesViewModel("o", "r", repository, sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        val draft = vm.state.value.editor!!.copy(tagName = "v3", body = "private A")
        sessions.value = first.copy(account = first.account.copy(id = 2))
        vm.onAction(saveEditor(draft))
        assertNull("Stale save must not enter pending state", vm.state.value.pendingMutation)
        runCurrent()
        assertTrue(repository.created.isEmpty())
    }

    @Test fun queuedSaveChecksLiveAccountBeforeRepositoryCall() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val repository = FakeReleasesRepository()
        val vm = ReleasesViewModel("o", "r", repository, sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        vm.onAction(ReleasesAction.OpenEditor())
        val draft = vm.state.value.editor!!.copy(tagName = "v3", body = "private A")
        vm.onAction(saveEditor(draft))
        assertTrue(vm.state.value.isMutating)
        sessions.value = first.copy(account = first.account.copy(id = 2))
        runCurrent()
        assertTrue("Queued save must not call the repository", repository.created.isEmpty())
    }

    @Test fun allReleaseWritesRejectImmediateQueuedAndPreviouslyConfirmedAccountChanges() = runTest(dispatcher) {
        for (phase in 0..2) for (operation in 0..4) {
            val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
            val sessions = MutableStateFlow<GithubSession>(first)
            val repository = FakeReleasesRepository()
            val vm = ReleasesViewModel("o", "r", repository, sessions, TestGithubRepositoryDetailsRepository())
            val detail = ReleaseDetailViewModel("o", "r", ReleaseReference.Id(1), repository, sessions, TestGithubRepositoryDetailsRepository())
            runCurrent()
            fun write() = submitAccountWrite(operation, vm, detail)
            if (phase == 1) {
                write()
                assertTrue(if (operation == 4) detail.state.value.isRemovingAsset else vm.state.value.isMutating)
            }
            sessions.value = first.copy(account = first.account.copy(id = 2))
            if (phase == 2) runCurrent()
            if (phase != 1) write()
            runCurrent()
            assertTrue(repository.created.isEmpty())
            assertTrue(repository.updated.isEmpty())
            assertTrue(repository.deleted.isEmpty())
            assertTrue(repository.uploaded.isEmpty())
            assertTrue(repository.removedAssets.isEmpty())
        }
    }

    @Test fun allMatchingReleaseWritesSelectTheBoundRepository() = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val repository = FakeReleasesRepository()
        val vm = ReleasesViewModel("o", "r", repository, sessions, TestGithubRepositoryDetailsRepository())
        val detail = ReleaseDetailViewModel("o", "r", ReleaseReference.Id(1), repository, sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        for (operation in 0..4) { submitAccountWrite(operation, vm, detail); runCurrent() }
        assertEquals(listOf(1L, 1L, 1L, 1L, 1L), repository.boundAccounts)
        assertEquals(1, repository.created.size)
        assertEquals(1, repository.updated.size)
        assertEquals(listOf(1L), repository.deleted)
        assertEquals(1, repository.uploaded.size)
        assertEquals(listOf(10L), repository.removedAssets)
    }

    private fun submitAccountWrite(operation: Int, vm: ReleasesViewModel, detail: ReleaseDetailViewModel) {
        when (operation) {
            0, 1 -> vm.onAction(ReleasesAction.Save(if (operation == 0) null else 1L, "v3", "Title", "Private A", "main", true, false, 1L))
            2 -> vm.onAction(ReleasesAction.Delete(1, 1L))
            3 -> vm.onAction(ReleasesAction.Attach(1, "private.bin", "", "application/octet-stream", 1,
                { error("Fake never opens a stream") }, 1L))
            4 -> detail.onAction(ReleaseDetailAction.RemoveAsset(10, 1L))
        }
    }

    private fun saveEditor(draft: ReleaseEditorDraft) = ReleasesAction.Save(
        draft.releaseId, draft.tagName, draft.title, draft.body, draft.targetCommitish,
        draft.isDraft, draft.isPrerelease, draft.accountId
    )

    @Test fun uncertainCreateCannotBeRepeatedWithoutNewConsent() = uncertainWriteRequiresConsent(0)
    @Test fun uncertainEditCannotBeRepeatedWithoutNewConsent() = uncertainWriteRequiresConsent(1)
    @Test fun uncertainReleaseDeletionCannotBeRepeatedWithoutNewConsent() = uncertainWriteRequiresConsent(2)
    @Test fun uncertainAssetUploadCannotBeRepeatedWithoutNewConsent() = uncertainWriteRequiresConsent(3)
    @Test fun uncertainAssetDeletionCannotBeRepeatedWithoutNewConsent() = uncertainWriteRequiresConsent(4)

    private fun uncertainWriteRequiresConsent(operation: Int) = runTest(dispatcher) {
        val first = GithubSession.SignedIn(GithubAccount(1, "same", null, null, "", "", 0, 0, 0))
        val sessions = MutableStateFlow<GithubSession>(first)
        val repo = FakeReleasesRepository().apply { writeFailure = java.io.IOException("Response lost") }
        val vm = ReleasesViewModel("o", "r", repo, sessions, TestGithubRepositoryDetailsRepository())
        val detail = ReleaseDetailViewModel("o", "r", ReleaseReference.Id(1), repo, sessions, TestGithubRepositoryDetailsRepository())
        runCurrent()
        submitAccountWrite(operation, vm, detail); runCurrent()
        assertEquals(1, repo.created.size + repo.updated.size + repo.deleted.size + repo.uploaded.size + repo.removedAssets.size)
        repo.writeFailure = null
        vm.onAction(ReleasesAction.Refresh)
        detail.onAction(ReleaseDetailAction.Retry)
        runCurrent()
        submitAccountWrite(operation, vm, detail); runCurrent()
        assertEquals("A lost release result needs explicit new consent", 1,
            repo.created.size + repo.updated.size + repo.deleted.size + repo.uploaded.size + repo.removedAssets.size)
    }

    @Test fun uncertainReleaseOutcomeSurvivesViewModelRecreationUntilExplicitRepeatConsent() = runTest(dispatcher) {
        val journal = InMemoryGithubActionJournal()
        val repo = FakeReleasesRepository().apply { writeFailure = java.io.IOException("Response lost") }
        val first = ReleasesViewModel("o", "r", repo, journal = journal)
        runCurrent()
        first.onAction(ReleasesAction.Save(null, "v3", "Title", "Body", "main", true, false))
        runCurrent()
        assertEquals(1, repo.created.size)
        repo.writeFailure = null
        val recreated = ReleasesViewModel("o", "r", repo, journal = journal)
        runCurrent()
        assertTrue(recreated.state.value.unconfirmedOperations.contains(
            GithubActionOperation(GithubActionKind.RELEASE_CREATE, 1L)))
        recreated.onAction(ReleasesAction.Save(null, "v3", "Title", "Body", "main", true, false))
        runCurrent()
        assertEquals("Recreation must not silently repeat an uncertain release", 1, repo.created.size)
        recreated.onAction(ReleasesAction.Save(null, "v3", "Title", "Body", "main", true, false, repeatConfirmed = true))
        runCurrent()
        assertEquals(2, repo.created.size)
    }

    private class FakeReleasesRepository : GithubReleasesRepository {
        val boundAccounts = mutableListOf<Long?>()
        override fun forAccount(accountId: Long?): GithubReleasesRepository { boundAccounts += accountId; return this }
        val pages = mutableListOf<Int>()
        val refreshRequests = mutableListOf<Int>()
        val tags = mutableListOf<String>()
        val created = mutableListOf<GithubReleaseDraft>()
        val updated = mutableListOf<Pair<Long, GithubReleaseDraft>>()
        val deleted = mutableListOf<Long>()
        val detailLoads = mutableListOf<Long>()
        val removedAssets = mutableListOf<Long>()
        val uploaded = mutableListOf<Pair<Long, GithubReleaseAssetUpload>>()
        var failSecondPage = false
        var failRefresh = false
        var writeFailure: Throwable? = null

        override suspend fun releases(
            owner: String,
            name: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubRelease>> {
            pages += page
            if (page == 2 && failSecondPage) {
                return Result.failure(IllegalStateException("page failed"))
            }
            return Result.success(
                GithubPage(
                    items = listOf(release(page.toLong())),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }

        override suspend fun refreshReleases(
            owner: String,
            name: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubRelease>> {
            refreshRequests += page
            if (failRefresh) {
                return Result.failure(IllegalStateException("Offline"))
            }
            return releases(owner, name, page, perPage)
        }

        override suspend fun release(owner: String, name: String, releaseId: Long): Result<GithubRelease> {
            detailLoads += releaseId
            return Result.success(release(releaseId))
        }

        override suspend fun releaseByTag(owner: String, name: String, tagName: String): Result<GithubRelease> {
            tags += tagName
            return Result.success(release(12))
        }

        override suspend fun createRelease(
            owner: String,
            name: String,
            draft: GithubReleaseDraft
        ): Result<GithubRelease> {
            created += draft
            return writeResult { release(1) }
        }

        override suspend fun updateRelease(
            owner: String,
            name: String,
            releaseId: Long,
            draft: GithubReleaseDraft
        ): Result<GithubRelease> {
            updated += releaseId to draft
            return writeResult { release(releaseId) }
        }

        override suspend fun deleteRelease(owner: String, name: String, releaseId: Long): Result<Unit> {
            deleted += releaseId
            return writeResult { }
        }

        override suspend fun uploadAsset(
            owner: String,
            name: String,
            releaseId: Long,
            asset: GithubReleaseAssetUpload
        ): Result<GithubReleaseAsset> {
            uploaded += releaseId to asset
            return writeResult {
                GithubReleaseAsset(
                    id = 900L + uploaded.size,
                    name = asset.fileName,
                    label = asset.label.takeIf(String::isNotEmpty),
                    contentType = asset.contentType,
                    sizeBytes = asset.contentLength,
                    downloadCount = 0,
                    createdAt = "2026-08-16T00:00:00Z",
                    downloadUrl = "https://github.com/openai/codex/releases/download/v1.0.0/${asset.fileName}"
                )
            }
        }

        override suspend fun deleteAsset(owner: String, name: String, assetId: Long): Result<Unit> {
            removedAssets += assetId
            return writeResult { }
        }

        private fun <T> writeResult(value: () -> T): Result<T> =
            writeFailure?.let { Result.failure(it) } ?: Result.success(value())
    }

    private companion object {
        fun release(id: Long) = GithubRelease(
            id = id,
            tagName = "v$id.0.0",
            targetCommitish = "main",
            name = "Release $id",
            body = "Highlights",
            author = GithubUserSummary("alice", null, "https://github.com/alice"),
            isDraft = false,
            isPrerelease = id == 1L,
            createdAt = "2026-08-15T00:00:00Z",
            publishedAt = "2026-08-16T00:00:00Z",
            htmlUrl = "https://github.com/openai/codex/releases/tag/v$id.0.0",
            assets = listOf(
                GithubReleaseAsset(
                    id = id * 10,
                    name = "asset-$id.zip",
                    label = null,
                    contentType = "application/zip",
                    sizeBytes = 1_024,
                    downloadCount = 3,
                    createdAt = "2026-08-16T00:00:00Z",
                    downloadUrl = "https://github.com/openai/codex/releases/download/v$id.0.0/asset-$id.zip"
                )
            )
        )
    }
}
