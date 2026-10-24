package takagi.ru.monica.github.feature.actions

import takagi.ru.monica.github.domain.GithubCollaboratorRole
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubActionsConclusion
import takagi.ru.monica.github.domain.GithubActionsLog
import takagi.ru.monica.github.domain.GithubActionsRepository
import takagi.ru.monica.github.domain.GithubActionsStatus
import takagi.ru.monica.github.domain.GithubArtifactOutput
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowArtifact
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowRunAction
import takagi.ru.monica.github.domain.GithubWorkflowState
import takagi.ru.monica.github.domain.GithubWorkflowStep
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class GithubActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun workflowAndRunListsAppendPagesWithoutDuplicates() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val workflowsViewModel = ActionsWorkflowsViewModel("openai", "codex", repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        workflowsViewModel.onAction(ActionsWorkflowsAction.LoadMore)
        advanceUntilIdle()

        val runsViewModel = WorkflowRunsViewModel("openai", "codex", 11, "Android CI", repository)
        advanceUntilIdle()
        runsViewModel.onAction(WorkflowRunsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), workflowsViewModel.state.value.items.map(GithubWorkflow::id))
        assertEquals(listOf(1L, 2L), runsViewModel.state.value.items.map(GithubWorkflowRun::id))
    }

    @Test
    fun workflowListTogglesEnabledStatePerWorkflow() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsWorkflowsViewModel("openai", "codex", repository, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1L, false))
        advanceUntilIdle()

        assertEquals(listOf(1L to false), repository.workflowStates)
        assertEquals(GithubWorkflowState.DISABLED_MANUALLY, viewModel.state.value.items.single().state)
        assertTrue(viewModel.state.value.workflowErrorIds.isEmpty())
    }

    @Test
    fun workflowRefreshKeepsExistingItemsAndUsesRefreshRepositoryPath() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsWorkflowsViewModel("openai", "codex", repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()

        viewModel.onAction(ActionsWorkflowsAction.Refresh)
        assertTrue(viewModel.state.value.isRefreshing)
        advanceUntilIdle()

        assertEquals(listOf(1), repository.refreshWorkflowRequests)
        assertEquals(1, viewModel.state.value.items.size)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun workflowListDispatchesRefAndInputsWithIndependentState() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsWorkflowsViewModel("openai", "codex", repository, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(
            ActionsWorkflowsAction.DispatchWorkflow(
                workflowId = 1L,
                ref = "main",
                inputs = mapOf("platform" to "android")
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(Triple(1L, "main", mapOf("platform" to "android"))), repository.dispatches)
        assertTrue(viewModel.state.value.dispatchBusyIds.isEmpty())
        assertTrue(viewModel.state.value.dispatchErrorIds.isEmpty())
    }

    @Test
    fun runDetailLoadsRunAndPagedJobs() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsRunDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        viewModel.onAction(ActionsRunDetailAction.LoadMoreJobs)
        advanceUntilIdle()

        assertEquals("Run 1", viewModel.state.value.run?.displayTitle)
        assertEquals(listOf(1L, 2L), viewModel.state.value.jobs.map(GithubWorkflowJob::id))
        assertFalse(viewModel.state.value.isLoadingJobs)
    }

    @Test
    fun runDetailPerformsAndRefreshesAWorkflowAction() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsRunDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(
            ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN)
        )
        advanceUntilIdle()

        assertEquals(listOf(GithubWorkflowRunAction.RERUN), repository.runActions)
        assertFalse(viewModel.state.value.isPerformingAction)
        assertFalse(viewModel.state.value.actionError)
    }

    @Test
    fun runDetailLoadsAndPagesArtifacts() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsRunDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        viewModel.onAction(ActionsRunDetailAction.LoadMoreArtifacts)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.artifacts.map(GithubWorkflowArtifact::id))
        assertFalse(viewModel.state.value.isLoadingArtifacts)
        assertFalse(viewModel.state.value.artifactsError)
    }

    @Test
    fun artifactFailureLeavesJobsUsable() = runTest(dispatcher) {
        val viewModel = ActionsRunDetailViewModel(
            "openai", "codex", 1, FakeActionsRepository(failArtifacts = true),
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.artifactsError)
        assertEquals(listOf(1L), viewModel.state.value.jobs.map(GithubWorkflowJob::id))
    }

    @Test
    fun downloadingAnArtifactStreamsBytesThenFreesTheSlot() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsRunDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        val output = RecordingArtifactOutput()

        viewModel.onAction(ActionsRunDetailAction.DownloadArtifact(1L) { output })
        assertEquals(1L, viewModel.state.value.downloadingArtifactId)
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.downloads)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.bytes.toByteArray())
        assertTrue(output.closed)
        assertNull(viewModel.state.value.downloadingArtifactId)
        assertFalse(viewModel.state.value.artifactDownloadFailed)
    }

    @Test
    fun anInterruptedDownloadNeverOpensTheFile() = runTest(dispatcher) {
        val viewModel = ActionsRunDetailViewModel(
            "openai", "codex", 1, FakeActionsRepository(failDownload = true),
            TestGithubRepositoryDetailsRepository()
        )
        advanceUntilIdle()
        val output = RecordingArtifactOutput()
        var openCount = 0

        viewModel.onAction(ActionsRunDetailAction.DownloadArtifact(1L) {
            openCount += 1
            output
        })
        advanceUntilIdle()

        assertEquals(0, openCount)
        assertNull(viewModel.state.value.downloadingArtifactId)
        assertTrue(viewModel.state.value.artifactDownloadFailed)
    }

    @Test
    fun onlyOneArtifactDownloadsAtATime() = runTest(dispatcher) {
        val repository = FakeActionsRepository()
        val viewModel = ActionsRunDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        val output = RecordingArtifactOutput()

        viewModel.onAction(ActionsRunDetailAction.DownloadArtifact(1L) { output })
        viewModel.onAction(ActionsRunDetailAction.DownloadArtifact(2L) { output })
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.downloads)
    }

    @Test
    fun jobDetailKeepsJobVisibleWhenLogFails() = runTest(dispatcher) {
        val repository = FakeActionsRepository(failLog = true)
        val viewModel = ActionsJobDetailViewModel("openai", "codex", 1, repository)
        advanceUntilIdle()

        assertEquals("job-1", viewModel.state.value.job?.name)
        assertTrue(viewModel.state.value.logError)
        assertEquals(null, viewModel.state.value.log)
    }

    @Test
    fun backgroundWorkflowObserverRevokesSameLoginAccountPermissionsBeforeRoleReturns() = runTest(dispatcher) {
        val first = signedInSession("same-login")
        val sessions = MutableStateFlow<GithubSession>(first)
        val details = TestGithubRepositoryDetailsRepository()
        val actions = FakeActionsRepository()
        val vm = ActionsWorkflowsViewModel("o", "r", actions, details, sessions)
        runCurrent()
        assertTrue(vm.state.value.canManageWorkflows)
        details.viewerRole = GithubCollaboratorRole.READ
        sessions.value = first.copy(account = first.account.copy(id = 2))
        // This VM is not collected by any screen; only its permanent auth observer runs.
        runCurrent()
        assertEquals(2L, vm.state.value.viewerAccountId)
        assertFalse(vm.state.value.canManageWorkflows)
        vm.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1, false, 1L))
        runCurrent()
        assertTrue(actions.workflowStates.isEmpty())
        sessions.value = GithubSession.SignedOut
        runCurrent()
        assertFalse(vm.state.value.canManageWorkflows)
    }

    @Test
    fun latePrivateWorkflowReadCannotPopulateAnotherAccount() = runTest(dispatcher) {
        val first = signedInSession("same-login")
        val sessions = MutableStateFlow<GithubSession>(first)
        lateinit var response: Continuation<Result<GithubPage<GithubWorkflow>>>
        val base = FakeActionsRepository()
        val repository = object : GithubActionsRepository by base {
            override fun forAccount(accountId: Long?): GithubActionsRepository = this
            override suspend fun workflows(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubWorkflow>> =
                if ((sessions.value as? GithubSession.SignedIn)?.account?.id == 1L)
                    suspendCoroutine { response = it }
                else Result.failure(IllegalStateException("private repo denied"))
        }
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
        runCurrent()
        sessions.value = first.copy(account = first.account.copy(id = 2))
        runCurrent()
        response.resume(base.workflows("o", "r", 1, 30))
        runCurrent()
        assertEquals(2L, vm.state.value.viewerAccountId)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun oldWorkflowWriteCannotChangeNewAccountsWorkflow() = runTest(dispatcher) {
        val first = signedInSession("same-login")
        val sessions = MutableStateFlow<GithubSession>(first)
        lateinit var response: Continuation<Result<Unit>>
        val base = FakeActionsRepository()
        val repository = object : GithubActionsRepository by base {
            override fun forAccount(accountId: Long?): GithubActionsRepository = this
            override suspend fun setWorkflowEnabled(owner: String, name: String, workflowId: Long, enabled: Boolean): Result<Unit> =
                suspendCoroutine { response = it }
        }
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
        runCurrent()
        vm.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1, false, 1L))
        runCurrent()
        sessions.value = first.copy(account = first.account.copy(id = 2))
        runCurrent()
        response.resume(Result.success(Unit))
        runCurrent()
        assertEquals(GithubWorkflowState.ACTIVE, vm.state.value.items.single().state)
        assertTrue(vm.state.value.workflowBusyIds.isEmpty())
    }

    @Test fun liveAccountChangeRejectsWorkflowDispatchBeforeCollectorRuns() = runTest(dispatcher) {
        val first = signedInSession("same-login")
        val sessions = MutableStateFlow<GithubSession>(first)
        val repository = FakeActionsRepository()
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
        runCurrent()
        sessions.value = first.copy(account = first.account.copy(id = 2))
        vm.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L))
        assertTrue("Stale action must not enter pending state", vm.state.value.dispatchBusyIds.isEmpty())
        runCurrent()
        assertTrue(repository.dispatches.isEmpty())
    }

    @Test fun queuedWorkflowDispatchChecksLiveAccountBeforeRepositoryCall() = runTest(dispatcher) {
        val first = signedInSession("same-login")
        val sessions = MutableStateFlow<GithubSession>(first)
        val repository = FakeActionsRepository()
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
        runCurrent()
        vm.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L))
        assertEquals(setOf(1L), vm.state.value.dispatchBusyIds)
        sessions.value = first.copy(account = first.account.copy(id = 2))
        runCurrent()
        assertTrue("Queued write must not call the repository", repository.dispatches.isEmpty())
    }

    @Test fun toggleAndRunRejectImmediateQueuedAndPreviouslyConfirmedAccountChanges() = runTest(dispatcher) {
        for (phase in 0..2) {
            val first = signedInSession("same-login")
            val sessions = MutableStateFlow<GithubSession>(first)
            val repository = FakeActionsRepository()
            val workflows = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
            val run = ActionsRunDetailViewModel("o", "r", 1, repository, TestGithubRepositoryDetailsRepository(), sessions)
            runCurrent()
            fun write() {
                workflows.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1, false, 1L))
                run.onAction(ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN, 1L))
            }
            if (phase == 1) {
                write()
                assertEquals(setOf(1L), workflows.state.value.workflowBusyIds)
                assertTrue(run.state.value.isPerformingAction)
            }
            sessions.value = first.copy(account = first.account.copy(id = 2))
            if (phase == 2) runCurrent()
            if (phase != 1) write()
            runCurrent()
            assertTrue(repository.workflowStates.isEmpty())
            assertTrue(repository.runActions.isEmpty())
        }
    }

    @Test fun everyMatchingAccountActionSelectsTheBoundRepository() = runTest(dispatcher) {
        val sessions = MutableStateFlow<GithubSession>(signedInSession("same-login"))
        val repository = FakeActionsRepository()
        val workflows = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions)
        val run = ActionsRunDetailViewModel("o", "r", 1, repository, TestGithubRepositoryDetailsRepository(), sessions)
        runCurrent()
        workflows.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1, false, 1L))
        workflows.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L))
        run.onAction(ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN, 1L))
        runCurrent()
        assertEquals(listOf(1L, 1L, 1L), repository.boundAccounts)
        assertEquals(listOf(1L to false), repository.workflowStates)
        assertEquals(1, repository.dispatches.size)
        assertEquals(listOf(GithubWorkflowRunAction.RERUN), repository.runActions)
    }

    @Test fun uncertainDispatchCannotBeRepeatedWithoutExplicitConsent() = runTest(dispatcher) {
        val repository = FakeActionsRepository().apply { writeFailure = java.io.IOException("response lost") }
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository())
        vm.onSessionChanged(signedInSession("same-login")); runCurrent()
        val action = ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap())
        vm.onAction(action); runCurrent()
        vm.onAction(action); runCurrent()
        assertEquals("A lost result must require new consent", 1, repository.dispatches.size)
    }

    @Test fun uncertainRunCannotBeRepeatedWithoutExplicitConsent() = runTest(dispatcher) {
        val repository = FakeActionsRepository().apply { writeFailure = java.io.IOException("response lost") }
        val vm = ActionsRunDetailViewModel("o", "r", 1, repository, TestGithubRepositoryDetailsRepository())
        vm.onSessionChanged(signedInSession("same-login")); runCurrent()
        val action = ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN)
        vm.onAction(action); runCurrent()
        vm.onAction(action); runCurrent()
        assertEquals("A lost result must require new consent", 1, repository.runActions.size)
    }

    @Test fun uncertainToggleCannotBeRepeatedWithoutExplicitConsent() = runTest(dispatcher) {
        val repository = FakeActionsRepository().apply { writeFailure = java.io.IOException("response lost") }
        val vm = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository())
        vm.onSessionChanged(signedInSession("same-login")); runCurrent()
        val action = ActionsWorkflowsAction.SetWorkflowEnabled(1, false)
        vm.onAction(action); runCurrent()
        vm.onAction(action); runCurrent()
        assertEquals("A lost result must require new consent", 1, repository.workflowStates.size)
    }

    @Test fun pendingDispatchSurvivesANewViewModelAndReadRefreshUntilExplicitRepeat() = runTest(dispatcher) {
        val journal = takagi.ru.monica.github.data.InMemoryGithubActionJournal()
        val repository = FakeActionsRepository().apply { writeFailure = java.io.IOException("response lost") }
        val sessions = MutableStateFlow<GithubSession>(signedInSession("same-login"))
        fun newModel() = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions, journal)
        val first = newModel(); runCurrent()
        first.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L)); runCurrent()
        val restored = newModel(); runCurrent()
        assertEquals(1, restored.state.value.unconfirmedOperations.size)
        restored.onAction(ActionsWorkflowsAction.Refresh); runCurrent()
        restored.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L)); runCurrent()
        assertEquals(1, repository.dispatches.size)
        assertEquals(1, restored.state.value.unconfirmedOperations.size)
        repository.writeFailure = null
        restored.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L, repeatConfirmed = true)); runCurrent()
        assertEquals(2, repository.dispatches.size)
        assertTrue(restored.state.value.unconfirmedOperations.isEmpty())
        assertTrue(first.state.value.unconfirmedOperations.isEmpty())
    }

    @Test fun runWarningIsScopedToNumericAccountAndAcknowledgementNeverWritesRemotely() = runTest(dispatcher) {
        val journal = takagi.ru.monica.github.data.InMemoryGithubActionJournal()
        val repository = FakeActionsRepository().apply { writeFailure = java.io.IOException("response lost") }
        val original = signedInSession("same-login") as GithubSession.SignedIn
        val sessions = MutableStateFlow<GithubSession>(original)
        val vm = ActionsRunDetailViewModel("o", "r", 1, repository, TestGithubRepositoryDetailsRepository(), sessions, journal)
        runCurrent()
        vm.onAction(ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN, 1L)); runCurrent()
        assertTrue(vm.state.value.unconfirmedAction)
        sessions.value = GithubSession.SignedIn(original.account.copy(id = 2)); runCurrent()
        assertFalse(vm.state.value.unconfirmedAction)
        vm.onAction(ActionsRunDetailAction.Acknowledge(1L)); runCurrent()
        sessions.value = original; runCurrent()
        assertTrue(vm.state.value.unconfirmedAction)
        vm.onAction(ActionsRunDetailAction.RetryRun); runCurrent()
        assertTrue(vm.state.value.unconfirmedAction)
        vm.onAction(ActionsRunDetailAction.Acknowledge(1L)); runCurrent()
        assertFalse(vm.state.value.unconfirmedAction)
        assertEquals(listOf(GithubWorkflowRunAction.RERUN), repository.runActions)
    }

    @Test fun unreadableJournalDisablesAllActionWrites() = runTest(dispatcher) {
        val journal = object : takagi.ru.monica.github.domain.GithubActionJournal {
            override fun observe(scope: takagi.ru.monica.github.domain.GithubActionScope) =
                kotlinx.coroutines.flow.flow<Set<takagi.ru.monica.github.domain.GithubActionOperation>> { throw java.io.IOException("unreadable") }
            override suspend fun begin(scope: takagi.ru.monica.github.domain.GithubActionScope, operation: takagi.ru.monica.github.domain.GithubActionOperation, repeatConfirmed: Boolean): takagi.ru.monica.github.domain.GithubActionAttempt = error("Must not begin")
            override suspend fun finish(attempt: takagi.ru.monica.github.domain.GithubActionAttempt, acceptedSuccess: Boolean) = error("Must not finish")
            override suspend fun acknowledge(scope: takagi.ru.monica.github.domain.GithubActionScope, operation: takagi.ru.monica.github.domain.GithubActionOperation) = error("Must not acknowledge")
        }
        val repository = FakeActionsRepository()
        val sessions = MutableStateFlow<GithubSession>(signedInSession("same-login"))
        val list = ActionsWorkflowsViewModel("o", "r", repository, TestGithubRepositoryDetailsRepository(), sessions, journal)
        val run = ActionsRunDetailViewModel("o", "r", 1, repository, TestGithubRepositoryDetailsRepository(), sessions, journal)
        runCurrent()
        assertTrue(list.state.value.journalUnavailable)
        assertTrue(run.state.value.journalUnavailable)
        list.onAction(ActionsWorkflowsAction.SetWorkflowEnabled(1, false, 1L, true))
        list.onAction(ActionsWorkflowsAction.DispatchWorkflow(1, "main", emptyMap(), 1L, true))
        run.onAction(ActionsRunDetailAction.PerformRunAction(GithubWorkflowRunAction.RERUN, 1L, true))
        runCurrent()
        assertTrue(repository.workflowStates.isEmpty() && repository.dispatches.isEmpty() && repository.runActions.isEmpty())
    }

    private class FakeActionsRepository(
        private val failLog: Boolean = false,
        private val failArtifacts: Boolean = false,
        private val failDownload: Boolean = false
    ) : GithubActionsRepository {
        var writeFailure: Throwable? = null
        val boundAccounts = mutableListOf<Long?>()
        override fun forAccount(accountId: Long?): GithubActionsRepository { boundAccounts += accountId; return this }
        val runActions = mutableListOf<GithubWorkflowRunAction>()
        val workflowStates = mutableListOf<Pair<Long, Boolean>>()
        val dispatches = mutableListOf<Triple<Long, String, Map<String, String>>>()
        val refreshWorkflowRequests = mutableListOf<Int>()
        val downloads = mutableListOf<Long>()
        override suspend fun workflows(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(workflow(page.toLong())), if (page == 1) 2 else null))

        override suspend fun refreshWorkflows(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(workflow(page.toLong())).also { refreshWorkflowRequests += page }, if (page == 1) 2 else null))

        override suspend fun workflowRuns(
            owner: String,
            name: String,
            workflowId: Long,
            page: Int,
            perPage: Int
        ) = Result.success(GithubPage(listOf(run(page.toLong())), if (page == 1) 2 else null))

        override suspend fun workflowRun(owner: String, name: String, runId: Long) =
            Result.success(run(runId))

        override suspend fun jobs(owner: String, name: String, runId: Long, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(job(page.toLong(), runId)), if (page == 1) 2 else null))

        override suspend fun job(owner: String, name: String, jobId: Long) =
            Result.success(job(jobId, 1))

        override suspend fun jobLog(owner: String, name: String, jobId: Long): Result<GithubActionsLog> =
            if (failLog) Result.failure(IllegalStateException("unavailable"))
            else Result.success(GithubActionsLog("line one\nline two", isTruncated = false))

        override suspend fun artifacts(
            owner: String,
            name: String,
            runId: Long,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubWorkflowArtifact>> =
            if (failArtifacts) Result.failure(IllegalStateException("unavailable"))
            else Result.success(
                GithubPage(listOf(artifact(page.toLong())), if (page == 1) 2 else null)
            )

        override suspend fun downloadArtifact(
            owner: String,
            name: String,
            artifactId: Long,
            open: () -> GithubArtifactOutput
        ): Result<Unit> {
            downloads += artifactId
            if (failDownload) return Result.failure(IllegalStateException("interrupted"))
            val output = open()
            output.write(byteArrayOf(1, 2, 3), 0, 3)
            output.close()
            return Result.success(Unit)
        }

        override suspend fun performRunAction(
            owner: String,
            name: String,
            runId: Long,
            action: GithubWorkflowRunAction
        ): Result<Unit> {
            runActions += action
            return writeFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun setWorkflowEnabled(
            owner: String,
            name: String,
            workflowId: Long,
            enabled: Boolean
        ): Result<Unit> {
            workflowStates += workflowId to enabled
            return writeFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun dispatchWorkflow(
            owner: String,
            name: String,
            workflowId: Long,
            ref: String,
            inputs: Map<String, String>
        ): Result<Unit> {
            dispatches += Triple(workflowId, ref, inputs)
            return writeFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }

    /** Stands in for the document uri the user picks; records what a download actually wrote. */
    private class RecordingArtifactOutput : GithubArtifactOutput {
        val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(source: ByteArray, offset: Int, count: Int) {
            bytes.write(source, offset, count)
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        fun artifact(id: Long) = GithubWorkflowArtifact(
            id = id,
            name = "artifact-$id",
            sizeBytes = 1_024 * id,
            isExpired = false,
            createdAt = "2026-08-16T00:00:00Z"
        )

        fun workflow(id: Long) = GithubWorkflow(
            id = id,
            name = "Workflow $id",
            path = ".github/workflows/$id.yml",
            state = GithubWorkflowState.ACTIVE,
            htmlUrl = "https://github.com/openai/codex/actions/workflows/$id.yml",
            badgeUrl = null,
            createdAt = "2026-08-16T00:00:00Z",
            updatedAt = "2026-08-16T00:00:00Z"
        )

        fun run(id: Long) = GithubWorkflowRun(
            id = id,
            workflowId = 11,
            name = "Android CI",
            displayTitle = "Run $id",
            runNumber = id.toInt(),
            event = "push",
            status = GithubActionsStatus.COMPLETED,
            conclusion = GithubActionsConclusion.SUCCESS,
            headBranch = "main",
            headSha = "sha-$id",
            actor = GithubUserSummary("alice", null, "https://github.com/alice"),
            createdAt = "2026-08-16T00:00:00Z",
            updatedAt = "2026-08-16T00:01:00Z",
            runStartedAt = "2026-08-16T00:00:10Z",
            htmlUrl = "https://github.com/openai/codex/actions/runs/$id"
        )

        fun job(id: Long, runId: Long) = GithubWorkflowJob(
            id = id,
            runId = runId,
            name = "job-$id",
            status = GithubActionsStatus.COMPLETED,
            conclusion = GithubActionsConclusion.SUCCESS,
            startedAt = "2026-08-16T00:00:00Z",
            completedAt = "2026-08-16T00:01:00Z",
            htmlUrl = "https://github.com/openai/codex/actions/runs/$runId/job/$id",
            runnerName = "runner",
            labels = listOf("ubuntu-latest"),
            steps = listOf(
                GithubWorkflowStep(
                    number = 1,
                    name = "Checkout",
                    status = GithubActionsStatus.COMPLETED,
                    conclusion = GithubActionsConclusion.SUCCESS,
                    startedAt = null,
                    completedAt = null
                )
            )
        )
    }

    private fun signedInSession(login: String) = GithubSession.SignedIn(
        GithubAccount(
            id = 1,
            login = login,
            name = null,
            bio = null,
            avatarUrl = "https://avatars.githubusercontent.com/u/1",
            htmlUrl = "https://github.com/$login",
            publicRepositories = 0,
            followers = 0,
            following = 0
        )
    )

}
