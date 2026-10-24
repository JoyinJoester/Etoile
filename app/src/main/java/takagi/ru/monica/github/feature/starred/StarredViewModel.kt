package takagi.ru.monica.github.feature.starred

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubLabeledStar
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarFilter
import takagi.ru.monica.github.domain.GithubStarLabel
import takagi.ru.monica.github.domain.GithubStarLabelError
import takagi.ru.monica.github.domain.GithubStarLabelException
import takagi.ru.monica.github.domain.GithubStarLabelStore
import takagi.ru.monica.github.domain.GithubStarsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class StarredUiState(
    val repositories: List<GithubLabeledStar> = emptyList(),
    val labels: List<GithubStarLabel> = emptyList(),
    val selectedFilter: GithubStarFilter = GithubStarFilter.All,
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val requiresAuthentication: Boolean = true,
    val error: Boolean = false,
    val refreshError: Boolean = false,
    val editingRepositoryId: Long? = null,
    val labelManagerVisible: Boolean = false,
    val labelError: GithubStarLabelError? = null,
    val labelErrorTargetId: Long? = null,
    val selectionMode: Boolean = false,
    val selectedRepositories: Set<Long> = emptySet(),
    val batchLabelVisible: Boolean = false
) {
    /**
     * Rows for the active filter and query. Computed once per state change rather
     * than on every recomposition, which matters once a user has thousands of
     * stars and the filter row itself asks for a count per label.
     */
    val visibleRepositories: List<GithubLabeledStar> by lazy {
        repositories.filter { item -> item.matches(selectedFilter) && matchesQuery(item) }
    }

    /** Repository counts per label id, plus the unlabeled bucket, in one pass. */
    private val countsByLabelId: Map<Long, Int> by lazy {
        val counts = mutableMapOf<Long, Int>()
        repositories.forEach { item ->
            item.labels.forEach { label ->
                counts[label.id] = (counts[label.id] ?: 0) + 1
            }
        }
        counts
    }

    val unlabeledCount: Int by lazy { repositories.count { it.labels.isEmpty() } }

    private val repositoriesById: Map<Long, GithubLabeledStar> by lazy {
        repositories.associateBy { it.repository.id }
    }

    val editingRepository: GithubLabeledStar?
        get() = editingRepositoryId?.let(repositoriesById::get)

    fun count(label: GithubStarLabel): Int = countsByLabelId[label.id] ?: 0

    private fun matchesQuery(item: GithubLabeledStar): Boolean {
        if (query.isBlank()) return true
        return item.repository.fullName.contains(query, ignoreCase = true) ||
            item.repository.description.orEmpty().contains(query, ignoreCase = true) ||
            item.labels.any { it.name.contains(query, ignoreCase = true) }
    }

    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore && !isRefreshing && !requiresAuthentication

    /** Local filters may hide a whole page, so the empty viewport must also allow continuation. */
    internal fun nextAutomaticPage(lastVisibleIndex: Int, totalItems: Int): Int? =
        nextPage?.takeIf {
            canLoadMore && !error && totalItems > 0 && lastVisibleIndex >= 0 &&
                lastVisibleIndex >= totalItems - 6
        }

    val selectionCount: Int get() = selectedRepositories.size

    /** True once every visible row is selected, which drives the select-all toggle. */
    val allVisibleSelected: Boolean
        get() = visibleRepositories.isNotEmpty() &&
            visibleRepositories.all { it.repository.id in selectedRepositories }

    /**
     * How many of the selected rows already carry [label]. The batch sheet uses
     * this to show whether applying the label would add to or clear the selection.
     */
    fun selectedCount(label: GithubStarLabel): Int =
        repositories.count { it.repository.id in selectedRepositories && it.labels.any { l -> l.id == label.id } }
}

sealed interface StarredAction {
    data class QueryChanged(val query: String) : StarredAction
    data class FilterSelected(val filter: GithubStarFilter) : StarredAction
    data class LabelsRequested(val repositoryId: Long) : StarredAction
    data class LabelToggled(val repositoryId: Long, val labelId: Long) : StarredAction
    data class LabelCreated(val name: String) : StarredAction
    data class LabelRenamed(val labelId: Long, val name: String) : StarredAction
    data class LabelDeleted(val labelId: Long) : StarredAction
    data object LabelEditorDismissed : StarredAction
    data object LabelManagerRequested : StarredAction
    data object LabelManagerDismissed : StarredAction
    data object LabelErrorDismissed : StarredAction
    data object Retry : StarredAction
    data object PullToRefresh : StarredAction
    data object LoadMore : StarredAction
    data object LoadMoreAutomatically : StarredAction
    data class SelectionToggled(val repositoryId: Long) : StarredAction
    data object SelectionCleared : StarredAction
    data object SelectAllVisibleToggled : StarredAction
    data object BatchLabelRequested : StarredAction
    data object BatchLabelDismissed : StarredAction
    data class BatchLabelApplied(val labelId: Long, val assigned: Boolean) : StarredAction
}

class StarredViewModel(
    private val repository: GithubStarsRepository,
    private val labelStore: GithubStarLabelStore
) : ViewModel() {
    private val _state = MutableStateFlow(StarredUiState())
    val state: StateFlow<StarredUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadVersion = 0L
    private var sessionAccountId: Long? = null
    private var accountLabelStore: GithubStarLabelStore? = null

    fun onSessionChanged(session: GithubSession) {
        loadVersion++
        loadJob?.cancel()
        when (session) {
            GithubSession.Loading -> {
                sessionAccountId = null
                accountLabelStore = null
                _state.value = StarredUiState(isLoading = true)
            }
            GithubSession.SignedOut -> {
                sessionAccountId = null
                accountLabelStore = null
                _state.value = StarredUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> {
                sessionAccountId = null
                accountLabelStore = null
                _state.value = StarredUiState(error = true)
            }
            is GithubSession.SignedIn -> {
                val changedAccount = sessionAccountId != session.account.id
                sessionAccountId = session.account.id
                accountLabelStore = labelStore.forAccount(session.account.id)
                if (changedAccount) _state.value = StarredUiState(requiresAuthentication = false)
                else _state.update { it.copy(requiresAuthentication = false) }
                refresh()
            }
        }
    }

    fun onAction(action: StarredAction) {
        if (sessionAccountId == null) return
        when (action) {
            is StarredAction.QueryChanged -> _state.update { it.copy(query = action.query) }
            is StarredAction.FilterSelected -> _state.update { it.copy(selectedFilter = action.filter) }
            is StarredAction.LabelsRequested -> _state.update {
                it.copy(editingRepositoryId = action.repositoryId, labelError = null)
            }
            is StarredAction.LabelToggled -> toggleLabel(action.repositoryId, action.labelId)
            is StarredAction.LabelCreated -> createLabel(action.name)
            is StarredAction.LabelRenamed -> renameLabel(action.labelId, action.name)
            is StarredAction.LabelDeleted -> deleteLabel(action.labelId)
            StarredAction.LabelEditorDismissed -> _state.update {
                it.copy(editingRepositoryId = null, labelError = null)
            }
            StarredAction.LabelManagerRequested -> _state.update {
                it.copy(labelManagerVisible = true, labelError = null)
            }
            StarredAction.LabelManagerDismissed -> _state.update {
                it.copy(labelManagerVisible = false, labelError = null)
            }
            StarredAction.LabelErrorDismissed -> _state.update { it.copy(labelError = null) }
            StarredAction.Retry -> if (_state.value.repositories.isEmpty() || _state.value.refreshError) {
                load(reset = true, refreshing = _state.value.repositories.isNotEmpty())
            } else {
                load(reset = false)
            }
            StarredAction.PullToRefresh -> load(reset = true, refreshing = true)
            StarredAction.LoadMore -> load(reset = false)
            StarredAction.LoadMoreAutomatically -> if (!_state.value.error) load(reset = false)
            is StarredAction.SelectionToggled -> toggleSelection(action.repositoryId)
            StarredAction.SelectionCleared -> _state.update {
                it.copy(
                    selectionMode = false,
                    selectedRepositories = emptySet(),
                    batchLabelVisible = false
                )
            }
            StarredAction.SelectAllVisibleToggled -> toggleSelectAllVisible()
            StarredAction.BatchLabelRequested -> _state.update {
                it.copy(batchLabelVisible = it.selectedRepositories.isNotEmpty(), labelError = null)
            }
            StarredAction.BatchLabelDismissed -> _state.update {
                it.copy(batchLabelVisible = false, labelError = null)
            }
            is StarredAction.BatchLabelApplied -> applyBatchLabel(action.labelId, action.assigned)
        }
    }

    /** Leaving selection mode when the last row is deselected keeps the bar honest. */
    private fun toggleSelection(repositoryId: Long) {
        if (_state.value.repositories.none { it.repository.id == repositoryId }) return
        _state.update { state ->
            val selected = if (repositoryId in state.selectedRepositories) {
                state.selectedRepositories - repositoryId
            } else {
                state.selectedRepositories + repositoryId
            }
            state.copy(
                selectedRepositories = selected,
                selectionMode = selected.isNotEmpty(),
                batchLabelVisible = state.batchLabelVisible && selected.isNotEmpty()
            )
        }
    }

    private fun toggleSelectAllVisible() {
        _state.update { state ->
            val visibleIds = state.visibleRepositories.map { it.repository.id }.toSet()
            val selected = if (state.allVisibleSelected) {
                state.selectedRepositories - visibleIds
            } else {
                state.selectedRepositories + visibleIds
            }
            state.copy(
                selectedRepositories = selected,
                selectionMode = selected.isNotEmpty(),
                batchLabelVisible = state.batchLabelVisible && selected.isNotEmpty()
            )
        }
    }

    private fun applyBatchLabel(labelId: Long, assigned: Boolean) {
        val targets = _state.value.selectedRepositories.intersect(
            _state.value.repositories.mapTo(hashSetOf()) { it.repository.id }
        )
        if (targets.isEmpty()) return
        val updated = accountLabelStore!!.assignLabel(targets, labelId, assigned)
        _state.update { state ->
            state.copy(
                repositories = state.repositories.map { item ->
                    updated[item.repository.id]?.let { item.copy(labels = it) } ?: item
                },
                labelError = null
            )
        }
    }

    private fun refresh() {
        load(reset = true)
    }

    /**
     * [refreshing] keeps the existing rows on screen while the pull gesture's own
     * indicator runs, instead of clearing the list into a skeleton.
     */
    private fun load(reset: Boolean, refreshing: Boolean = false) {
        val current = _state.value
        if (current.requiresAuthentication) return
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        val version = ++loadVersion
        _state.update {
            it.copy(
                repositories = if (reset && !refreshing) emptyList() else it.repositories,
                nextPage = if (reset) null else it.nextPage,
                labels = accountLabelStore!!.labels(),
                isLoading = reset && !refreshing,
                isLoadingMore = !reset,
                isRefreshing = refreshing,
                requiresAuthentication = false,
                error = false,
                refreshError = false
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.starredRepositories(requestedPage)
            currentCoroutineContext().ensureActive()
            if (version != loadVersion) return@launch
            result.fold(
                onSuccess = { page ->
                    val labeledPage = GithubPage(
                        items = page.items.map { repository ->
                            GithubLabeledStar(repository, accountLabelStore!!.labelsFor(repository.id))
                        },
                        nextPage = page.nextPage
                    )
                    _state.update { state ->
                        val repositories = labeledPage.mergeItems(state.repositories, reset, { it.repository.id })
                        val selected = state.selectedRepositories.intersect(repositories.map { it.repository.id }.toSet())
                        state.copy(
                            repositories = repositories,
                            selectedRepositories = selected,
                            selectionMode = selected.isNotEmpty(),
                            batchLabelVisible = state.batchLabelVisible && selected.isNotEmpty(),
                            editingRepositoryId = state.editingRepositoryId?.takeIf { id -> repositories.any { it.repository.id == id } },
                            labels = accountLabelStore!!.labels(),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = false,
                            refreshError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = true,
                            refreshError = reset
                        )
                    }
                }
            )
        }
    }

    private fun toggleLabel(repositoryId: Long, labelId: Long) {
        val assigned = accountLabelStore!!.toggleAssignment(repositoryId, labelId)
        _state.update { state ->
            state.copy(
                repositories = state.repositories.map { item ->
                    if (item.repository.id == repositoryId) item.copy(labels = assigned) else item
                }
            )
        }
    }

    private fun createLabel(name: String) {
        accountLabelStore!!.createLabel(name).fold(
            onSuccess = { _state.update { it.copy(labels = accountLabelStore!!.labels(), labelError = null) } },
            onFailure = { failure -> reportLabelFailure(failure) }
        )
    }

    private fun renameLabel(labelId: Long, name: String) {
        accountLabelStore!!.renameLabel(labelId, name).fold(
            onSuccess = { reloadLabels() },
            onFailure = { failure -> reportLabelFailure(failure, labelId) }
        )
    }

    private fun deleteLabel(labelId: Long) {
        accountLabelStore!!.deleteLabel(labelId)
        val labels = accountLabelStore!!.labels()
        _state.update { state ->
            val filter = state.selectedFilter
            state.copy(
                labels = labels,
                labelError = null,
                repositories = state.repositories.map { item ->
                    item.copy(labels = accountLabelStore!!.labelsFor(item.repository.id))
                },
                selectedFilter = if (filter is GithubStarFilter.Label && filter.labelId == labelId) {
                    GithubStarFilter.All
                } else {
                    filter
                }
            )
        }
    }

    /** Re-reads labels and refreshes the copies cached on each row. */
    private fun reloadLabels() {
        val labels = accountLabelStore!!.labels()
        _state.update { state ->
            state.copy(
                labels = labels,
                labelError = null,
                repositories = state.repositories.map { item ->
                    item.copy(labels = accountLabelStore!!.labelsFor(item.repository.id))
                }
            )
        }
    }

    private fun reportLabelFailure(failure: Throwable, labelId: Long? = null) {
        val error = (failure as? GithubStarLabelException)?.error ?: GithubStarLabelError.BLANK_NAME
        _state.update { it.copy(labelError = error, labelErrorTargetId = labelId) }
    }

    class Factory(
        private val repository: GithubStarsRepository,
        private val labelStore: GithubStarLabelStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StarredViewModel::class.java))
            return StarredViewModel(repository, labelStore) as T
        }
    }
}
