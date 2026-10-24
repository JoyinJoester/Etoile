package takagi.ru.monica.github.feature.starred

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarFilter
import takagi.ru.monica.github.domain.GithubStarLabel
import takagi.ru.monica.github.domain.GithubStarLabelError
import takagi.ru.monica.github.domain.GithubStarLabelException
import takagi.ru.monica.github.domain.GithubStarLabelStore
import takagi.ru.monica.github.domain.GithubStarsRepository
import takagi.ru.monica.github.domain.validateStarLabelName

@OptIn(ExperimentalCoroutinesApi::class)
class StarredViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun signedInSessionLoadsRepositoriesWithPersistedLabels() = runTest(dispatcher) {
        val store = FakeLabelStore().apply {
            val kotlin = createLabel("Kotlin").getOrThrow()
            toggleAssignment(1, kotlin.id)
        }
        val viewModel = StarredViewModel(FakeStarsRepository(), store)

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(
            listOf("Kotlin"),
            viewModel.state.value.repositories.single().labels.map { it.name }
        )
    }

    @Test
    fun togglingALabelPersistsAndUpdatesVisibleState() = runTest(dispatcher) {
        val store = FakeLabelStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Android"))
        val label = viewModel.state.value.labels.single()
        viewModel.onAction(StarredAction.LabelToggled(1, label.id))

        assertEquals(listOf(label), store.labelsFor(1))
        assertEquals(listOf(label), viewModel.state.value.repositories.single().labels)

        viewModel.onAction(StarredAction.LabelToggled(1, label.id))
        assertTrue(viewModel.state.value.repositories.single().labels.isEmpty())
    }

    @Test
    fun oneRepositoryCanCarrySeveralLabels() = runTest(dispatcher) {
        val store = FakeLabelStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Android"))
        viewModel.onAction(StarredAction.LabelCreated("Tools"))
        viewModel.state.value.labels.forEach { label ->
            viewModel.onAction(StarredAction.LabelToggled(1, label.id))
        }

        assertEquals(
            listOf("Android", "Tools"),
            viewModel.state.value.repositories.single().labels.map { it.name }
        )
    }

    @Test
    fun allUnlabeledAndLabelFiltersStayDistinct() = runTest(dispatcher) {
        val store = FakeLabelStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Kotlin"))
        val label = viewModel.state.value.labels.single()

        viewModel.onAction(StarredAction.FilterSelected(GithubStarFilter.Unlabeled))
        assertEquals(1, viewModel.state.value.visibleRepositories.size)

        viewModel.onAction(StarredAction.LabelToggled(1, label.id))
        assertEquals(0, viewModel.state.value.visibleRepositories.size)

        viewModel.onAction(StarredAction.FilterSelected(GithubStarFilter.Label(label.id)))
        assertEquals(1, viewModel.state.value.visibleRepositories.size)

        viewModel.onAction(StarredAction.FilterSelected(GithubStarFilter.All))
        assertEquals(1, viewModel.state.value.visibleRepositories.size)
    }

    @Test
    fun duplicateLabelNamesAreRejectedWithoutCreatingALabel() = runTest(dispatcher) {
        val viewModel = StarredViewModel(FakeStarsRepository(), FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Kotlin"))
        viewModel.onAction(StarredAction.LabelCreated("  kotlin "))

        assertEquals(1, viewModel.state.value.labels.size)
        assertEquals(GithubStarLabelError.DUPLICATE_NAME, viewModel.state.value.labelError)
    }

    @Test
    fun deletingTheActiveLabelFallsBackToTheAllFilter() = runTest(dispatcher) {
        val viewModel = StarredViewModel(FakeStarsRepository(), FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Kotlin"))
        val label = viewModel.state.value.labels.single()
        viewModel.onAction(StarredAction.LabelToggled(1, label.id))
        viewModel.onAction(StarredAction.FilterSelected(GithubStarFilter.Label(label.id)))

        viewModel.onAction(StarredAction.LabelDeleted(label.id))

        assertEquals(GithubStarFilter.All, viewModel.state.value.selectedFilter)
        assertTrue(viewModel.state.value.labels.isEmpty())
        assertTrue(viewModel.state.value.repositories.single().labels.isEmpty())
    }

    @Test
    fun renamingALabelRefreshesTheCopiesCachedOnEachRow() = runTest(dispatcher) {
        val viewModel = StarredViewModel(FakeStarsRepository(), FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Kotlin"))
        val label = viewModel.state.value.labels.single()
        viewModel.onAction(StarredAction.LabelToggled(1, label.id))

        viewModel.onAction(StarredAction.LabelRenamed(label.id, "Multiplatform"))

        assertEquals(listOf("Multiplatform"), viewModel.state.value.labels.map { it.name })
        assertEquals(
            listOf("Multiplatform"),
            viewModel.state.value.repositories.single().labels.map { it.name }
        )
    }

    @Test
    fun renameValidationErrorBelongsToTheEditedLabel() = runTest(dispatcher) {
        val viewModel = StarredViewModel(FakeStarsRepository(), FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(StarredAction.LabelCreated("Kotlin"))
        viewModel.onAction(StarredAction.LabelCreated("Tools"))
        val label = viewModel.state.value.labels.first()

        viewModel.onAction(StarredAction.LabelRenamed(label.id, "Tools"))
        assertEquals(label.id, viewModel.state.value.labelErrorTargetId)
        assertTrue(viewModel.state.value.labelError != null)
        assertEquals("Kotlin", viewModel.state.value.labels.first().name)

        viewModel.onAction(StarredAction.LabelCreated("Tools"))
        assertEquals(null, viewModel.state.value.labelErrorTargetId)
        assertTrue(viewModel.state.value.labelError != null)

        viewModel.onAction(StarredAction.LabelRenamed(label.id, "Libraries"))
        assertEquals(null, viewModel.state.value.labelError)
        assertEquals("Libraries", viewModel.state.value.labels.first().name)
    }

    @Test
    fun loadMoreAppendsStarredRepositoriesAndLabelsEachPage() = runTest(dispatcher) {
        val store = FakeLabelStore().apply {
            val tools = createLabel("Tools").getOrThrow()
            toggleAssignment(2, tools.id)
        }
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.repositories.map { it.repository.id })
        assertEquals(
            listOf("Tools"),
            viewModel.state.value.repositories.last().labels.map { it.name }
        )
    }

    @Test
    fun batchApplyLabelsEveryRepositoryInTheSelection() = runTest(dispatcher) {
        val store = FakeLabelStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Android"))
        val label = viewModel.state.value.labels.single()
        viewModel.onAction(StarredAction.SelectionToggled(1))
        viewModel.onAction(StarredAction.SelectionToggled(2))
        viewModel.onAction(StarredAction.BatchLabelApplied(label.id, assigned = true))

        assertEquals(listOf(label), store.labelsFor(1))
        assertEquals(listOf(label), store.labelsFor(2))
        assertTrue(viewModel.state.value.repositories.all { it.labels == listOf(label) })
    }

    @Test
    fun batchClearRemovesTheLabelFromTheWholeSelection() = runTest(dispatcher) {
        val store = FakeLabelStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), store)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LabelCreated("Android"))
        val label = viewModel.state.value.labels.single()
        viewModel.onAction(StarredAction.SelectionToggled(1))
        viewModel.onAction(StarredAction.SelectionToggled(2))
        viewModel.onAction(StarredAction.BatchLabelApplied(label.id, assigned = true))
        viewModel.onAction(StarredAction.BatchLabelApplied(label.id, assigned = false))

        assertTrue(store.labelsFor(1).isEmpty())
        assertTrue(store.labelsFor(2).isEmpty())
        assertTrue(viewModel.state.value.repositories.all { it.labels.isEmpty() })
    }

    @Test
    fun leavingSelectionModeClearsThePickedRows() = runTest(dispatcher) {
        val viewModel = StarredViewModel(FakeStarsRepository(), FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.SelectionToggled(1))
        assertEquals(1, viewModel.state.value.selectionCount)

        viewModel.onAction(StarredAction.SelectionCleared)
        assertEquals(0, viewModel.state.value.selectionCount)
        assertFalse(viewModel.state.value.selectionMode)
    }

    @Test
    fun failedPullToRefreshMarksTheErrorAsRefreshSoRetryReplaysFirstPage() = runTest(dispatcher) {
        val repository = FakeStarsRepository()
        val viewModel = StarredViewModel(repository, FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        repository.failNext = IllegalStateException("Offline")
        viewModel.onAction(StarredAction.PullToRefresh)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error)
        assertTrue(viewModel.state.value.refreshError)
        assertEquals(listOf(1L, 2L), viewModel.state.value.repositories.map { it.repository.id })

        repository.failNext = null
        viewModel.onAction(StarredAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 1, 1), repository.requested)
        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshError)
    }

    @Test
    fun failedPaginationKeepsTheErrorAsPaginationSoRetryContinuesTheList() = runTest(dispatcher) {
        val repository = FakeStarsRepository()
        val viewModel = StarredViewModel(repository, FakeLabelStore())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        repository.failNext = IllegalStateException("Offline")
        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshError)

        repository.failNext = null
        viewModel.onAction(StarredAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 2), repository.requested)
        assertEquals(listOf(1L, 2L), viewModel.state.value.repositories.map { it.repository.id })
    }

    @Test fun automaticPagingContinuesPastOneHundredAndDeduplicatesThroughTheLastPage() = runTest(dispatcher) {
        val repository = FakeStarsRepository().apply {
            pageBlock = { page -> Result.success(GithubPage(
                (when (page) { 1 -> 1..100; 2 -> 100..199; else -> 200..257 }).map { Companion.repository(it.toLong()) },
                if (page < 3) page + 1 else null
            )) }
        }
        val store = FakeLabelStore()
        val label = store.createLabel("Later page").getOrThrow()
        store.toggleAssignment(257, label.id)
        val vm = StarredViewModel(repository, store)
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        assertEquals(100, vm.state.value.repositories.size)
        assertEquals(null, vm.state.value.nextAutomaticPage(5, 102))
        repeat(2) {
            val total = vm.state.value.repositories.size + 2
            assertEquals(it + 2, vm.state.value.nextAutomaticPage(total - 2, total))
            vm.onAction(StarredAction.LoadMoreAutomatically)
            vm.onAction(StarredAction.LoadMoreAutomatically)
            advanceUntilIdle()
        }
        assertEquals((1L..257L).toList(), vm.state.value.repositories.map { it.repository.id })
        assertEquals(listOf(label), vm.state.value.repositories.last().labels)
        assertEquals(null, vm.state.value.nextAutomaticPage(258, 259))
        vm.onAction(StarredAction.LoadMoreAutomatically); advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), repository.requested)
    }

    @Test fun emptyLocalSearchKeepsPagingUntilAMatchBeyondTheFirstPage() = runTest(dispatcher) {
        val repository = FakeStarsRepository().apply {
            pageBlock = { page -> Result.success(GithubPage(
                ((page - 1) * 100 + 1..page * 100).map { Companion.repository(it.toLong()) },
                if (page < 3) page + 1 else null
            )) }
        }
        val vm = StarredViewModel(repository, FakeLabelStore())
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        vm.onAction(StarredAction.QueryChanged("etoile-250"))
        repeat(2) {
            assertTrue(vm.state.value.visibleRepositories.isEmpty())
            assertEquals(it + 2, vm.state.value.nextAutomaticPage(1, 2))
            vm.onAction(StarredAction.LoadMoreAutomatically); advanceUntilIdle()
        }
        assertEquals(listOf(250L), vm.state.value.visibleRepositories.map { it.repository.id })
        assertEquals(listOf(1, 2, 3), repository.requested)
    }

    @Test fun automaticPagingStopsOnFailureUntilExplicitRetry() = runTest(dispatcher) {
        val repository = FakeStarsRepository()
        val vm = StarredViewModel(repository, FakeLabelStore())
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        repository.failNext = IllegalStateException("offline")
        vm.onAction(StarredAction.LoadMoreAutomatically); advanceUntilIdle()
        assertEquals(null, vm.state.value.nextAutomaticPage(2, 3))
        repeat(3) { vm.onAction(StarredAction.LoadMoreAutomatically) }
        advanceUntilIdle()
        assertEquals(listOf(1, 2), repository.requested)
        assertEquals(1, vm.state.value.repositories.size)
        repository.failNext = null
        vm.onAction(StarredAction.Retry); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), repository.requested)
        assertEquals(2, vm.state.value.repositories.size)
    }

    @Test fun automaticPagingPausesDuringRefreshAndRejectsLateResultsAfterSignOut() = runTest(dispatcher) {
        val repository = FakeStarsRepository()
        val vm = StarredViewModel(repository, FakeLabelStore())
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        val delayed = CompletableDeferred<Result<GithubPage<GithubRepository>>>()
        repository.pageBlock = { withContext(NonCancellable) { delayed.await() } }
        vm.onAction(StarredAction.PullToRefresh); runCurrent()
        assertEquals(null, vm.state.value.nextAutomaticPage(2, 3))
        vm.onAction(StarredAction.LoadMoreAutomatically); runCurrent()
        assertEquals(listOf(1, 1), repository.requested)
        vm.onSessionChanged(GithubSession.SignedOut)
        delayed.complete(Result.success(GithubPage(listOf(Companion.repository(999)), 2)))
        advanceUntilIdle()
        assertTrue(vm.state.value.requiresAuthentication)
        assertTrue(vm.state.value.repositories.isEmpty())
        vm.onAction(StarredAction.LoadMoreAutomatically); advanceUntilIdle()
        assertEquals(listOf(1, 1), repository.requested)
    }

    @Test fun switchingAccountsResetsEveryAccountOwnedControlAndUsesScopedLabels() = runTest(dispatcher) {
        val firstStore = FakeLabelStore().apply { createLabel("Private collection").getOrThrow() }
        val secondStore = FakeLabelStore().apply { createLabel("Second account label").getOrThrow() }
        val rootStore = object : GithubStarLabelStore by firstStore {
            override fun forAccount(accountId: Long): GithubStarLabelStore = if (accountId == 1L) firstStore else secondStore
        }
        val vm = StarredViewModel(FakeStarsRepository(), rootStore)
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        vm.onAction(StarredAction.SelectionToggled(1))
        vm.onAction(StarredAction.BatchLabelRequested)
        vm.onAction(StarredAction.LabelsRequested(1))
        vm.onAction(StarredAction.LabelManagerRequested)
        vm.onAction(StarredAction.QueryChanged("private query"))
        vm.onAction(StarredAction.FilterSelected(GithubStarFilter.Label(firstStore.labels().single().id)))
        vm.onSessionChanged(GithubSession.SignedIn(account().copy(id = 2)))
        assertEquals("", vm.state.value.query)
        assertEquals(GithubStarFilter.All, vm.state.value.selectedFilter)
        assertTrue(vm.state.value.selectedRepositories.isEmpty())
        assertFalse(vm.state.value.selectionMode)
        assertFalse(vm.state.value.labelManagerVisible)
        assertFalse(vm.state.value.batchLabelVisible)
        assertEquals(null, vm.state.value.editingRepositoryId)
        advanceUntilIdle()
        assertEquals(listOf("Second account label"), vm.state.value.labels.map { it.name })
        vm.onAction(StarredAction.LabelCreated("Second only"))
        assertEquals(listOf("Private collection"), firstStore.labels().map { it.name })
        assertEquals(listOf("Second account label", "Second only"), secondStore.labels().map { it.name })
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        assertEquals(listOf("Private collection"), vm.state.value.labels.map { it.name })
    }

    @Test fun sessionLoadingCancelsOldFetchAndRejectsLabelWrites() = runTest(dispatcher) {
        val delayed = CompletableDeferred<Result<GithubPage<GithubRepository>>>()
        val repository = FakeStarsRepository().apply { pageBlock = { withContext(NonCancellable) { delayed.await() } } }
        val store = FakeLabelStore()
        val vm = StarredViewModel(repository, store)
        vm.onSessionChanged(GithubSession.SignedIn(account())); runCurrent()
        vm.onSessionChanged(GithubSession.Loading)
        vm.onAction(StarredAction.LabelCreated("must not persist"))
        vm.onAction(StarredAction.PullToRefresh)
        delayed.complete(Result.success(GithubPage(listOf(Companion.repository(999)), null))); advanceUntilIdle()
        assertTrue(store.labels().isEmpty())
        assertTrue(vm.state.value.repositories.isEmpty())
        assertTrue(vm.state.value.requiresAuthentication)
        assertEquals(listOf(1), repository.requested)
    }

    @Test fun refreshPrunesSelectionsAndEditorForRepositoriesThatAreNoLongerStarred() = runTest(dispatcher) {
        val repository = FakeStarsRepository()
        val vm = StarredViewModel(repository, FakeLabelStore())
        vm.onSessionChanged(GithubSession.SignedIn(account())); advanceUntilIdle()
        vm.onAction(StarredAction.SelectionToggled(1))
        vm.onAction(StarredAction.BatchLabelRequested)
        vm.onAction(StarredAction.LabelsRequested(1))
        repository.pageBlock = { Result.success(GithubPage(emptyList(), null)) }
        vm.onAction(StarredAction.PullToRefresh); advanceUntilIdle()
        assertTrue(vm.state.value.selectedRepositories.isEmpty())
        assertFalse(vm.state.value.selectionMode)
        assertFalse(vm.state.value.batchLabelVisible)
        assertEquals(null, vm.state.value.editingRepositoryId)
    }

    private class FakeStarsRepository : GithubStarsRepository {
        val requested = mutableListOf<Int>()
        var failNext: Throwable? = null
        var pageBlock: (suspend (Int) -> Result<GithubPage<GithubRepository>>)? = null

        override suspend fun starredRepositories(page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
            requested += page
            failNext?.let { return Result.failure(it) }
            pageBlock?.let { return it(page) }
            return Result.success(
                GithubPage(
                    items = listOf(repository(page.toLong())),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }
    }

    /** Mirrors the preference store's semantics without touching Android APIs. */
    private class FakeLabelStore : GithubStarLabelStore {
        private val labels = mutableListOf<GithubStarLabel>()
        private val assignments = mutableMapOf<Long, MutableSet<Long>>()
        private var nextId = 1L

        override fun labels(): List<GithubStarLabel> = labels.toList()

        override fun labelsFor(repositoryId: Long): List<GithubStarLabel> {
            val assigned = assignments[repositoryId].orEmpty()
            return labels.filter { it.id in assigned }
        }

        override fun createLabel(name: String): Result<GithubStarLabel> {
            val validated = validateStarLabelName(name, labels).getOrElse { return Result.failure(it) }
            val created = GithubStarLabel(nextId++, validated)
            labels += created
            return Result.success(created)
        }

        override fun renameLabel(labelId: Long, name: String): Result<GithubStarLabel> {
            val index = labels.indexOfFirst { it.id == labelId }
            if (index < 0) {
                return Result.failure(GithubStarLabelException(GithubStarLabelError.BLANK_NAME))
            }
            val validated = validateStarLabelName(name, labels, labelId)
                .getOrElse { return Result.failure(it) }
            val renamed = GithubStarLabel(labelId, validated)
            labels[index] = renamed
            return Result.success(renamed)
        }

        override fun deleteLabel(labelId: Long) {
            labels.removeAll { it.id == labelId }
            assignments.values.forEach { it.remove(labelId) }
        }

        override fun toggleAssignment(repositoryId: Long, labelId: Long): List<GithubStarLabel> {
            val assigned = assignments.getOrPut(repositoryId) { mutableSetOf() }
            if (!assigned.add(labelId)) assigned.remove(labelId)
            return labelsFor(repositoryId)
        }

        override fun assignLabel(
            repositoryIds: Set<Long>,
            labelId: Long,
            assigned: Boolean
        ): Map<Long, List<GithubStarLabel>> = repositoryIds.associateWith { repositoryId ->
            val current = assignments.getOrPut(repositoryId) { mutableSetOf() }
            if (assigned) current += labelId else current -= labelId
            labelsFor(repositoryId)
        }
    }

    private companion object {
        fun repository(id: Long = 1) = GithubRepository(
            id,
            "etoile-$id",
            "joyins/etoile-$id",
            "GitHub client",
            "Kotlin",
            100,
            "2026-08-16",
            false,
            "https://github.com/joyins/etoile-$id"
        )
    }

    private fun account() = GithubAccount(1, "joyins", "Joyins", null, "", "https://github.com/joyins", 1, 1, 1)
}
