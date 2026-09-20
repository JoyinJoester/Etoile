package takagi.ru.monica.github.feature.starred

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

    private class FakeStarsRepository : GithubStarsRepository {
        val requested = mutableListOf<Int>()
        var failNext: Throwable? = null

        override suspend fun starredRepositories(page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
            requested += page
            failNext?.let { return Result.failure(it) }
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
