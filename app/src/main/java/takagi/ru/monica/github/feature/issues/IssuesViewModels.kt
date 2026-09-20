package takagi.ru.monica.github.feature.issues

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubSortDirection
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class IssuesUiState(
    val owner: String,
    val name: String,
    val selectedState: GithubIssueState = GithubIssueState.OPEN,
    val searchQuery: String = "",
    val sort: GithubListSort = GithubListSort.UPDATED,
    val direction: GithubSortDirection = GithubSortDirection.DESC,
    val items: List<GithubIssue> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val listQuery = GithubIssueListQuery(selectedState, sort, direction)
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface IssuesAction {
    data class SelectState(val state: GithubIssueState) : IssuesAction
    data class SearchChanged(val query: String) : IssuesAction
    data class SelectOrdering(
        val sort: GithubListSort,
        val direction: GithubSortDirection
    ) : IssuesAction
    data object Retry : IssuesAction
    data object LoadMore : IssuesAction
}

class IssuesViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubIssuesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(IssuesUiState(owner = owner, name = name))
    val state: StateFlow<IssuesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: IssuesAction) {
        when (action) {
            is IssuesAction.SelectState -> selectState(action.state)
            is IssuesAction.SearchChanged -> searchChanged(action.query)
            is IssuesAction.SelectOrdering -> selectOrdering(action.sort, action.direction)
            IssuesAction.Retry -> load(reset = _state.value.items.isEmpty())
            IssuesAction.LoadMore -> load(reset = false)
        }
    }

    private fun searchChanged(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update { it.copy(searchQuery = query) }
        // One search per typed phrase rather than one request per keystroke.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            load(reset = true)
        }
    }

    private fun selectState(state: GithubIssueState) {
        if (state == _state.value.selectedState) return
        _state.update { it.copy(selectedState = state, items = emptyList(), nextPage = null) }
        load(reset = true)
    }

    private fun selectOrdering(sort: GithubListSort, direction: GithubSortDirection) {
        val current = _state.value
        if (sort == current.sort && direction == current.direction) return
        _state.update {
            it.copy(
                sort = sort,
                direction = direction,
                items = emptyList(),
                nextPage = null
            )
        }
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        val query = current.listQuery
        val text = current.searchQuery.trim()
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                error = false,
                items = if (reset) emptyList() else it.items
            )
        }
        loadJob = viewModelScope.launch {
            // A phrase has to go to the search endpoint; the issues endpoint has no text parameter.
            val result = if (text.isEmpty()) {
                repository.issues(owner, name, query, requestedPage)
            } else {
                repository.searchInRepository(owner, name, text, query, requestedPage)
            }
            result.fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubIssue::id),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubIssuesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(IssuesViewModel::class.java))
            return IssuesViewModel(owner, name, repository) as T
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}

@Immutable
data class IssueDetailUiState(
    val owner: String,
    val name: String,
    val number: Int,
    val issue: GithubIssue? = null,
    val comments: List<GithubIssueComment> = emptyList(),
    val nextCommentsPage: Int? = null,
    val isLoadingIssue: Boolean = true,
    val isLoadingComments: Boolean = true,
    val isLoadingMoreComments: Boolean = false,
    val commentDraft: String = "",
    val isSubmittingComment: Boolean = false,
    val commentValidationError: Boolean = false,
    val commentSubmitError: Boolean = false,
    val isUpdatingState: Boolean = false,
    val stateUpdateError: Boolean = false,
    val isUpdatingLock: Boolean = false,
    val lockUpdateError: Boolean = false,
    val availableLabels: List<GithubIssueLabel> = emptyList(),
    val nextLabelsPage: Int? = null,
    val labelsLoaded: Boolean = false,
    val isLoadingLabels: Boolean = false,
    val isLoadingMoreLabels: Boolean = false,
    val labelsError: Boolean = false,
    val isUpdatingLabels: Boolean = false,
    val labelsUpdateError: Boolean = false,
    val availableAssignees: List<GithubUserSummary> = emptyList(),
    val nextAssigneesPage: Int? = null,
    val assigneesLoaded: Boolean = false,
    val isLoadingAssignees: Boolean = false,
    val isLoadingMoreAssignees: Boolean = false,
    val assigneesError: Boolean = false,
    val isUpdatingAssignees: Boolean = false,
    val assigneesUpdateError: Boolean = false,
    val availableMilestones: List<GithubIssueMilestone> = emptyList(),
    val nextMilestonesPage: Int? = null,
    val milestonesLoaded: Boolean = false,
    val isLoadingMilestones: Boolean = false,
    val isLoadingMoreMilestones: Boolean = false,
    val milestonesError: Boolean = false,
    val isUpdatingMilestone: Boolean = false,
    val milestoneUpdateError: Boolean = false,
    val isUpdatingContent: Boolean = false,
    val contentValidationError: Boolean = false,
    val contentUpdateError: Boolean = false,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN,
    val commentMutationBusy: Boolean = false,
    val commentMutationError: Boolean = false,
    val reactionBusyCommentIds: Set<Long> = emptySet(),
    val reactionErrorCommentIds: Set<Long> = emptySet(),
    val activeReactions: Map<Long, Set<GithubReactionContent>> = emptyMap(),
    val issueError: Boolean = false,
    val commentsError: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMoreComments: Boolean get() =
        nextCommentsPage != null && !isLoadingComments && !isLoadingMoreComments

    private val isSignedIn: Boolean get() = viewerLogin != null

    private val isAuthor: Boolean
        get() = viewerLogin != null && viewerLogin == issue?.author?.login

    /** Labels, assignees and milestones only need triage, not push. */
    val canTriage: Boolean get() = isSignedIn && viewerRole.canTriage

    /** Authors may edit their own title and body without repository access. */
    val canEditContent: Boolean get() = isSignedIn && (isAuthor || viewerRole.canTriage)

    /** Authors may close or reopen their own issue. */
    val canChangeState: Boolean get() = isSignedIn && (isAuthor || viewerRole.canTriage)

    /** Locking a thread is a moderation action reserved for collaborators. */
    val canLock: Boolean get() = isSignedIn && viewerRole.canTriage

    /** True when at least one management action is available to the viewer. */
    val canManage: Boolean get() = canTriage || canEditContent || canChangeState || canLock
}

sealed interface IssueDetailAction {
    data class EditComment(val id: Long, val body: String) : IssueDetailAction
    data class DeleteComment(val id: Long) : IssueDetailAction
    data object RetryIssue : IssueDetailAction
    data object RetryComments : IssueDetailAction
    data object LoadMoreComments : IssueDetailAction
    data class CommentChanged(val comment: String) : IssueDetailAction
    data object SubmitComment : IssueDetailAction
    data object ToggleState : IssueDetailAction
    data object ToggleLock : IssueDetailAction
    data object LoadLabels : IssueDetailAction
    data object LoadMoreLabels : IssueDetailAction
    data class UpdateLabels(val names: List<String>) : IssueDetailAction
    data object LoadAssignees : IssueDetailAction
    data object LoadMoreAssignees : IssueDetailAction
    data class UpdateAssignees(val logins: List<String>) : IssueDetailAction
    data object LoadMilestones : IssueDetailAction
    data object LoadMoreMilestones : IssueDetailAction
    data class UpdateMilestone(val number: Int?) : IssueDetailAction
    data class UpdateContent(val title: String, val body: String) : IssueDetailAction
    data class ToggleCommentReaction(
        val commentId: Long,
        val content: GithubReactionContent
    ) : IssueDetailAction
}

class IssueDetailViewModel(
    private val owner: String,
    private val name: String,
    private val number: Int,
    private val repository: GithubIssuesRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(IssueDetailUiState(owner, name, number))
    val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()
    private var issueJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var commentsJob: Job? = null
    private var commentJob: Job? = null
    private var stateJob: Job? = null
    private var lockJob: Job? = null
    private var labelsJob: Job? = null
    private var labelsUpdateJob: Job? = null
    private var assigneesJob: Job? = null
    private var assigneesUpdateJob: Job? = null
    private var milestonesJob: Job? = null
    private var milestoneUpdateJob: Job? = null
    private var contentUpdateJob: Job? = null
    private val reactionJobs = mutableMapOf<Long, Job>()
    private var viewerAccountId: Long? = null
    private var sessionRevision = 0L

    init {
        loadIssue()
        loadComments(reset = true)
    }

    fun onAction(action: IssueDetailAction) {
        when (action) {
            is IssueDetailAction.EditComment -> mutateComment(action.id, action.body)
            is IssueDetailAction.DeleteComment -> mutateComment(action.id, null)
            IssueDetailAction.RetryIssue -> loadIssue()
            IssueDetailAction.RetryComments -> loadComments(reset = _state.value.comments.isEmpty())
            IssueDetailAction.LoadMoreComments -> loadComments(reset = false)
            is IssueDetailAction.CommentChanged -> if (action.comment.length <= GithubIssueCommentDraft.MAX_BODY_LENGTH) {
                _state.update {
                    it.copy(
                        commentDraft = action.comment,
                        commentValidationError = false,
                        commentSubmitError = false
                    )
                }
            }
            IssueDetailAction.SubmitComment -> submitComment()
            IssueDetailAction.ToggleState -> toggleState()
            IssueDetailAction.ToggleLock -> toggleLock()
            IssueDetailAction.LoadLabels -> if (!_state.value.labelsLoaded || _state.value.labelsError) {
                loadLabels(reset = true)
            }
            IssueDetailAction.LoadMoreLabels -> loadLabels(reset = false)
            is IssueDetailAction.UpdateLabels -> updateLabels(action.names)
            IssueDetailAction.LoadAssignees -> if (!_state.value.assigneesLoaded || _state.value.assigneesError) {
                loadAssignees(reset = true)
            }
            IssueDetailAction.LoadMoreAssignees -> loadAssignees(reset = false)
            is IssueDetailAction.UpdateAssignees -> updateAssignees(action.logins)
            IssueDetailAction.LoadMilestones -> if (!_state.value.milestonesLoaded || _state.value.milestonesError) {
                loadMilestones(reset = true)
            }
            IssueDetailAction.LoadMoreMilestones -> loadMilestones(reset = false)
            is IssueDetailAction.UpdateMilestone -> updateMilestone(action.number)
            is IssueDetailAction.UpdateContent -> updateContent(action.title, action.body)
            is IssueDetailAction.ToggleCommentReaction -> toggleCommentReaction(action)
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        val login = account?.login
        if (account?.id == viewerAccountId && login == _state.value.viewerLogin) return
        viewerAccountId = account?.id
        sessionRevision++
        viewerRoleJob?.cancel()
        commentJob?.cancel()
        stateJob?.cancel()
        lockJob?.cancel()
        labelsUpdateJob?.cancel()
        assigneesUpdateJob?.cancel()
        milestoneUpdateJob?.cancel()
        contentUpdateJob?.cancel()
        reactionJobs.values.forEach { it.cancel() }
        reactionJobs.clear()
        _state.update {
            it.copy(
                viewerLogin = login,
                viewerRole = GithubCollaboratorRole.UNKNOWN,
                isSubmittingComment = false,
                commentValidationError = false,
                commentSubmitError = false,
                isUpdatingState = false,
                stateUpdateError = false,
                isUpdatingLock = false,
                lockUpdateError = false,
                isUpdatingLabels = false,
                labelsUpdateError = false,
                isUpdatingAssignees = false,
                assigneesUpdateError = false,
                isUpdatingMilestone = false,
                milestoneUpdateError = false,
                commentMutationBusy = false,
                commentMutationError = false,
                isUpdatingContent = false,
                contentValidationError = false,
                contentUpdateError = false,
                reactionBusyCommentIds = emptySet(),
                reactionErrorCommentIds = emptySet(),
                activeReactions = emptyMap()
            )
        }
        loadViewerRole(signedIn = login != null)
    }

    private fun submitComment() {
        val state = _state.value
        if (state.isSubmittingComment || state.issue?.isLocked == true) return
        val draft = GithubIssueCommentDraft.fromInput(state.commentDraft).getOrElse {
            _state.update { it.copy(commentValidationError = true, commentSubmitError = false) }
            return
        }
        _state.update {
            it.copy(
                isSubmittingComment = true,
                commentValidationError = false,
                commentSubmitError = false
            )
        }
        commentJob?.cancel()
        val revision = sessionRevision
        commentJob = viewModelScope.launch {
            val result = repository.addComment(owner, name, number, draft)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { comment ->
                    _state.update {
                        it.copy(
                            comments = (it.comments + comment).distinctBy(GithubIssueComment::id),
                            commentDraft = if (it.commentDraft == state.commentDraft) "" else it.commentDraft,
                            isSubmittingComment = false,
                            commentSubmitError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isSubmittingComment = false, commentSubmitError = true) }
                }
            )
        }
    }

    private fun toggleState() {
        val currentIssue = _state.value.issue ?: return
        if (!_state.value.canChangeState) return
        if (_state.value.isUpdatingState) return
        val target = if (currentIssue.state == GithubIssueState.OPEN) {
            GithubIssueState.CLOSED
        } else {
            GithubIssueState.OPEN
        }
        _state.update { it.copy(isUpdatingState = true, stateUpdateError = false) }
        stateJob?.cancel()
        val revision = sessionRevision
        stateJob = viewModelScope.launch {
            val result = repository.updateIssueState(owner, name, number, target)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update { it.copy(issue = issue, isUpdatingState = false, stateUpdateError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingState = false, stateUpdateError = true) }
                }
            )
        }
    }

    private fun toggleLock() {
        val currentIssue = _state.value.issue ?: return
        if (!_state.value.canLock || _state.value.isUpdatingLock) return
        val target = !currentIssue.isLocked
        _state.update { it.copy(isUpdatingLock = true, lockUpdateError = false) }
        lockJob?.cancel()
        val revision = sessionRevision
        lockJob = viewModelScope.launch {
            val result = repository.updateIssueLock(owner, name, number, target)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update { it.copy(issue = issue, isUpdatingLock = false, lockUpdateError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingLock = false, lockUpdateError = true) }
                }
            )
        }
    }

    private fun loadLabels(reset: Boolean) {
        val current = _state.value
        if (!reset && (current.nextLabelsPage == null || current.isLoadingLabels || current.isLoadingMoreLabels)) return
        val page = if (reset) 1 else current.nextLabelsPage ?: return
        labelsJob?.cancel()
        _state.update {
            it.copy(
                availableLabels = if (reset) emptyList() else it.availableLabels,
                isLoadingLabels = reset,
                isLoadingMoreLabels = !reset,
                labelsError = false,
                labelsLoaded = true
            )
        }
        labelsJob = viewModelScope.launch {
            repository.labels(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            availableLabels = result.mergeItems(
                                state.availableLabels,
                                reset,
                                GithubIssueLabel::name
                            ),
                            nextLabelsPage = result.nextPage,
                            isLoadingLabels = false,
                            isLoadingMoreLabels = false,
                            labelsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(isLoadingLabels = false, isLoadingMoreLabels = false, labelsError = true)
                    }
                }
            )
        }
    }

    private fun updateLabels(names: List<String>) {
        if (!_state.value.canTriage || _state.value.isUpdatingLabels) return
        _state.update { it.copy(isUpdatingLabels = true, labelsUpdateError = false) }
        labelsUpdateJob?.cancel()
        val revision = sessionRevision
        labelsUpdateJob = viewModelScope.launch {
            val result = repository.updateIssueLabels(owner, name, number, names)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update {
                        it.copy(issue = issue, isUpdatingLabels = false, labelsUpdateError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingLabels = false, labelsUpdateError = true) }
                }
            )
        }
    }

    private fun loadAssignees(reset: Boolean) {
        val current = _state.value
        if (!reset && (current.nextAssigneesPage == null || current.isLoadingAssignees || current.isLoadingMoreAssignees)) return
        val page = if (reset) 1 else current.nextAssigneesPage ?: return
        assigneesJob?.cancel()
        _state.update {
            it.copy(
                availableAssignees = if (reset) emptyList() else it.availableAssignees,
                isLoadingAssignees = reset,
                isLoadingMoreAssignees = !reset,
                assigneesError = false,
                assigneesLoaded = true
            )
        }
        assigneesJob = viewModelScope.launch {
            repository.assignees(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            availableAssignees = result.mergeItems(
                                state.availableAssignees,
                                reset,
                                GithubUserSummary::login
                            ),
                            nextAssigneesPage = result.nextPage,
                            isLoadingAssignees = false,
                            isLoadingMoreAssignees = false,
                            assigneesError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingAssignees = false,
                            isLoadingMoreAssignees = false,
                            assigneesError = true
                        )
                    }
                }
            )
        }
    }

    private fun updateAssignees(logins: List<String>) {
        if (!_state.value.canTriage || _state.value.isUpdatingAssignees) return
        _state.update { it.copy(isUpdatingAssignees = true, assigneesUpdateError = false) }
        assigneesUpdateJob?.cancel()
        val revision = sessionRevision
        assigneesUpdateJob = viewModelScope.launch {
            val result = repository.updateIssueAssignees(owner, name, number, logins)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update {
                        it.copy(issue = issue, isUpdatingAssignees = false, assigneesUpdateError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingAssignees = false, assigneesUpdateError = true) }
                }
            )
        }
    }

    private fun loadMilestones(reset: Boolean) {
        val current = _state.value
        if (!reset && (current.nextMilestonesPage == null || current.isLoadingMilestones || current.isLoadingMoreMilestones)) return
        val page = if (reset) 1 else current.nextMilestonesPage ?: return
        milestonesJob?.cancel()
        _state.update {
            it.copy(
                availableMilestones = if (reset) emptyList() else it.availableMilestones,
                isLoadingMilestones = reset,
                isLoadingMoreMilestones = !reset,
                milestonesError = false,
                milestonesLoaded = true
            )
        }
        milestonesJob = viewModelScope.launch {
            repository.milestones(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            availableMilestones = result.mergeItems(
                                state.availableMilestones,
                                reset,
                                GithubIssueMilestone::number
                            ),
                            nextMilestonesPage = result.nextPage,
                            isLoadingMilestones = false,
                            isLoadingMoreMilestones = false,
                            milestonesError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingMilestones = false,
                            isLoadingMoreMilestones = false,
                            milestonesError = true
                        )
                    }
                }
            )
        }
    }

    private fun updateMilestone(number: Int?) {
        if (!_state.value.canTriage || _state.value.isUpdatingMilestone) return
        _state.update { it.copy(isUpdatingMilestone = true, milestoneUpdateError = false) }
        milestoneUpdateJob?.cancel()
        val revision = sessionRevision
        milestoneUpdateJob = viewModelScope.launch {
            val result = repository.updateIssueMilestone(owner, name, this@IssueDetailViewModel.number, number)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update {
                        it.copy(issue = issue, isUpdatingMilestone = false, milestoneUpdateError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingMilestone = false, milestoneUpdateError = true) }
                }
            )
        }
    }

    private fun updateContent(title: String, body: String) {
        if (!_state.value.canEditContent || _state.value.isUpdatingContent) return
        val draft = GithubIssueDraft.fromInput(title, body).getOrElse {
            _state.update { it.copy(contentValidationError = true, contentUpdateError = false) }
            return
        }
        _state.update {
            it.copy(
                isUpdatingContent = true,
                contentValidationError = false,
                contentUpdateError = false
            )
        }
        contentUpdateJob?.cancel()
        val revision = sessionRevision
        contentUpdateJob = viewModelScope.launch {
            val result = repository.updateIssue(owner, name, number, draft)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { issue ->
                    _state.update {
                        it.copy(
                            issue = issue,
                            isUpdatingContent = false,
                            contentValidationError = false,
                            contentUpdateError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingContent = false, contentUpdateError = true) }
                }
            )
        }
    }

    private fun mutateComment(id: Long, body: String?) {
        val current = _state.value
        val comment = current.comments.firstOrNull { it.id == id } ?: return
        if (current.commentMutationBusy || current.viewerLogin == null ||
            !current.viewerLogin.equals(comment.author.login, true)) return
        val draft = body?.let { GithubIssueCommentDraft.fromInput(it).getOrNull() }
        if (body != null && draft == null) {
            _state.update { it.copy(commentMutationError = true) }
            return
        }
        _state.update { it.copy(commentMutationBusy = true, commentMutationError = false) }
        val revision = sessionRevision
        viewModelScope.launch {
            val result = if (draft == null) repository.deleteComment(owner, name, id).map { null }
                else repository.editComment(owner, name, id, draft)
            if (revision != sessionRevision || !isActive) return@launch
            result.fold(onSuccess = { updated ->
                _state.update { state -> state.copy(commentMutationBusy = false,
                    comments = state.comments.mapNotNull { if (it.id == id) updated else it },
                    issue = if (updated == null) state.issue?.let { it.copy(comments = (it.comments - 1).coerceAtLeast(0)) } else state.issue) }
            }, onFailure = { _state.update { it.copy(commentMutationBusy = false, commentMutationError = true) } })
        }
    }

    private fun toggleCommentReaction(action: IssueDetailAction.ToggleCommentReaction) {
        val viewerLogin = _state.value.viewerLogin ?: return
        if (action.commentId in _state.value.reactionBusyCommentIds) return
        _state.update {
            it.copy(
                reactionBusyCommentIds = it.reactionBusyCommentIds + action.commentId,
                reactionErrorCommentIds = it.reactionErrorCommentIds - action.commentId
            )
        }
        reactionJobs[action.commentId]?.cancel()
        val revision = sessionRevision
        reactionJobs[action.commentId] = viewModelScope.launch {
            val result = repository.toggleCommentReaction(
                owner = owner,
                name = name,
                commentId = action.commentId,
                content = action.content,
                viewerLogin = viewerLogin
            )
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { result ->
                    _state.update { state ->
                        val updatedComments = state.comments.map { comment ->
                            if (comment.id == action.commentId) {
                                comment.copy(
                                    reactions = comment.reactions.withDelta(
                                        action.content,
                                        if (result.active) 1 else -1
                                    )
                                )
                            } else {
                                comment
                            }
                        }
                        val contentSet = state.activeReactions[action.commentId].orEmpty()
                        val updatedContentSet = if (result.active) {
                            contentSet + action.content
                        } else {
                            contentSet - action.content
                        }
                        state.copy(
                            comments = updatedComments,
                            reactionBusyCommentIds = state.reactionBusyCommentIds - action.commentId,
                            reactionErrorCommentIds = state.reactionErrorCommentIds - action.commentId,
                            activeReactions = state.activeReactions +
                                (action.commentId to updatedContentSet)
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            reactionBusyCommentIds = it.reactionBusyCommentIds - action.commentId,
                            reactionErrorCommentIds = it.reactionErrorCommentIds + action.commentId
                        )
                    }
                }
            )
            reactionJobs.remove(action.commentId)
        }
    }

    override fun onCleared() {
        reactionJobs.values.forEach { it.cancel() }
        reactionJobs.clear()
        super.onCleared()
    }

    private fun loadViewerRole(signedIn: Boolean) {
        viewerRoleJob?.cancel()
        val revision = sessionRevision
        if (!signedIn) {
            _state.update { it.copy(viewerRole = GithubCollaboratorRole.UNKNOWN) }
            return
        }
        viewerRoleJob = viewModelScope.launch {
            val role = detailsRepository.details(owner, name)
                .getOrNull()
                ?.viewerRole
                ?: GithubCollaboratorRole.UNKNOWN
            if (!isActive || revision != sessionRevision) return@launch
            _state.update { it.copy(viewerRole = role) }
        }
    }

    private fun loadIssue() {
        issueJob?.cancel()
        _state.update { it.copy(isLoadingIssue = true, issueError = false) }
        issueJob = viewModelScope.launch {
            repository.issue(owner, name, number).fold(
                onSuccess = { issue ->
                    _state.update { it.copy(issue = issue, isLoadingIssue = false, issueError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingIssue = false, issueError = true) }
                }
            )
        }
    }

    private fun loadComments(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreComments) return
        val requestedPage = if (reset) 1 else current.nextCommentsPage ?: return
        commentsJob?.cancel()
        _state.update {
            it.copy(
                isLoadingComments = reset,
                isLoadingMoreComments = !reset,
                commentsError = false,
                comments = if (reset) emptyList() else it.comments
            )
        }
        commentsJob = viewModelScope.launch {
            repository.comments(owner, name, number, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            comments = page.mergeItems(state.comments, reset, GithubIssueComment::id),
                            nextCommentsPage = page.nextPage,
                            isLoadingComments = false,
                            isLoadingMoreComments = false,
                            commentsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingComments = false,
                            isLoadingMoreComments = false,
                            commentsError = true
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val number: Int,
        private val repository: GithubIssuesRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(IssueDetailViewModel::class.java))
            return IssueDetailViewModel(owner, name, number, repository, detailsRepository) as T
        }
    }
}
