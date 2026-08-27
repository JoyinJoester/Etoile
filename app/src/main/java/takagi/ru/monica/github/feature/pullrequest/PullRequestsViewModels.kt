package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubMergeMethod
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubMergeResult
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestListQuery
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubSortDirection
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubReviewEvent
import takagi.ru.monica.github.domain.GithubRequestedReviewersUpdate
import takagi.ru.monica.github.domain.mergeItems

enum class PullRequestDraftFilter { ALL, READY, DRAFT }

@Immutable
data class PullRequestsUiState(
    val owner: String,
    val name: String,
    val selectedState: GithubPullRequestState = GithubPullRequestState.OPEN,
    val searchQuery: String = "",
    val sort: GithubListSort = GithubListSort.UPDATED,
    val direction: GithubSortDirection = GithubSortDirection.DESC,
    val draftFilter: PullRequestDraftFilter = PullRequestDraftFilter.ALL,
    val items: List<GithubPullRequest> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val listQuery = GithubPullRequestListQuery(selectedState, sort, direction)
    val visibleItems: List<GithubPullRequest> = if (
        searchQuery.isBlank() && draftFilter == PullRequestDraftFilter.ALL
    ) {
        items
    } else {
        items.filter { pullRequest ->
            pullRequest.matches(draftFilter) && pullRequest.matchesLoadedSearch(searchQuery)
        }
    }
    val hasLocalFilters: Boolean
        get() = searchQuery.isNotBlank() || draftFilter != PullRequestDraftFilter.ALL
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface PullRequestsAction {
    data class SelectState(val state: GithubPullRequestState) : PullRequestsAction
    data class SearchChanged(val query: String) : PullRequestsAction
    data class SelectOrdering(
        val sort: GithubListSort,
        val direction: GithubSortDirection
    ) : PullRequestsAction
    data class SelectDraftFilter(val filter: PullRequestDraftFilter) : PullRequestsAction
    data object Retry : PullRequestsAction
    data object LoadMore : PullRequestsAction
}

class PullRequestsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubPullRequestsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PullRequestsUiState(owner, name))
    val state: StateFlow<PullRequestsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: PullRequestsAction) {
        when (action) {
            is PullRequestsAction.SelectState -> selectState(action.state)
            is PullRequestsAction.SearchChanged -> _state.update { it.copy(searchQuery = action.query) }
            is PullRequestsAction.SelectOrdering -> selectOrdering(action.sort, action.direction)
            is PullRequestsAction.SelectDraftFilter -> {
                _state.update { it.copy(draftFilter = action.filter) }
            }
            PullRequestsAction.Retry -> load(reset = _state.value.items.isEmpty())
            PullRequestsAction.LoadMore -> load(reset = false)
        }
    }

    private fun selectState(state: GithubPullRequestState) {
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
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.pullRequests(
                owner,
                name,
                query,
                requestedPage
            ).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubPullRequest::id),
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
        private val repository: GithubPullRequestsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PullRequestsViewModel::class.java))
            return PullRequestsViewModel(owner, name, repository) as T
        }
    }
}

private fun GithubPullRequest.matches(filter: PullRequestDraftFilter): Boolean = when (filter) {
    PullRequestDraftFilter.ALL -> true
    PullRequestDraftFilter.READY -> !isDraft
    PullRequestDraftFilter.DRAFT -> isDraft
}

private fun GithubPullRequest.matchesLoadedSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isEmpty()) return true
    val numberQuery = query.removePrefix("#").takeIf(String::isNotEmpty)
    return title.contains(query, ignoreCase = true) ||
        author.login.contains(query, ignoreCase = true) ||
        labels.any { it.name.contains(query, ignoreCase = true) } ||
        head.ref.contains(query, ignoreCase = true) ||
        base.ref.contains(query, ignoreCase = true) ||
        (numberQuery != null && number.toString().contains(numberQuery))
}

enum class PullRequestSection { OVERVIEW, FILES, ACTIVITY }

@Immutable
data class PullRequestDetailUiState(
    val owner: String,
    val name: String,
    val number: Int,
    val pullRequest: GithubPullRequest? = null,
    val files: List<GithubPullRequestFile> = emptyList(),
    val reviews: List<GithubPullRequestReview> = emptyList(),
    val reviewComments: List<GithubPullRequestReviewComment> = emptyList(),
    val comments: List<GithubIssueComment> = emptyList(),
    val nextFilesPage: Int? = null,
    val nextReviewsPage: Int? = null,
    val nextReviewCommentsPage: Int? = null,
    val nextCommentsPage: Int? = null,
    val selectedSection: PullRequestSection = PullRequestSection.OVERVIEW,
    val isLoadingPullRequest: Boolean = true,
    val isLoadingFiles: Boolean = true,
    val isLoadingReviews: Boolean = true,
    val isLoadingReviewComments: Boolean = true,
    val isLoadingComments: Boolean = true,
    val isLoadingMoreFiles: Boolean = false,
    val isLoadingMoreReviews: Boolean = false,
    val isLoadingMoreReviewComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val pullRequestError: Boolean = false,
    val filesError: Boolean = false,
    val reviewsError: Boolean = false,
    val reviewCommentsError: Boolean = false,
    val commentsError: Boolean = false,
    val reviewBody: String = "",
    val isSubmittingReview: Boolean = false,
    val reviewValidationError: Boolean = false,
    val reviewSubmitError: Boolean = false,
    val commentDraft: String = "",
    val isSubmittingComment: Boolean = false,
    val commentValidationError: Boolean = false,
    val commentSubmitError: Boolean = false,
    val isMerging: Boolean = false,
    val mergeError: Boolean = false,
    val mergeValidationError: Boolean = false,
    val mergeResult: GithubMergeResult? = null,
    val isUpdatingState: Boolean = false,
    val stateUpdateError: Boolean = false,
    val isUpdatingContent: Boolean = false,
    val contentValidationError: Boolean = false,
    val contentUpdateError: Boolean = false,
    val isUpdatingLock: Boolean = false,
    val lockUpdateError: Boolean = false,
    val availableLabels: List<GithubIssueLabel> = emptyList(),
    val nextLabelsPage: Int? = null,
    val isLoadingLabels: Boolean = false,
    val isLoadingMoreLabels: Boolean = false,
    val labelsError: Boolean = false,
    val isUpdatingLabels: Boolean = false,
    val labelsUpdateError: Boolean = false,
    val availableAssignees: List<GithubUserSummary> = emptyList(),
    val nextAssigneesPage: Int? = null,
    val isLoadingAssignees: Boolean = false,
    val isLoadingMoreAssignees: Boolean = false,
    val assigneesError: Boolean = false,
    val isUpdatingAssignees: Boolean = false,
    val assigneesUpdateError: Boolean = false,
    val availableMilestones: List<GithubIssueMilestone> = emptyList(),
    val nextMilestonesPage: Int? = null,
    val isLoadingMilestones: Boolean = false,
    val isLoadingMoreMilestones: Boolean = false,
    val milestonesError: Boolean = false,
    val isUpdatingMilestone: Boolean = false,
    val milestoneUpdateError: Boolean = false,
    val isUpdatingReviewers: Boolean = false,
    val reviewersValidationError: Boolean = false,
    val reviewersUpdateError: Boolean = false,
    val viewerLogin: String? = null,
    val reactionBusyCommentIds: Set<Long> = emptySet(),
    val reactionErrorCommentIds: Set<Long> = emptySet(),
    val activeReactions: Map<Long, Set<GithubReactionContent>> = emptyMap()
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMoreFiles: Boolean get() = nextFilesPage != null && !isLoadingFiles && !isLoadingMoreFiles
    val canLoadMoreReviews: Boolean get() = nextReviewsPage != null && !isLoadingReviews && !isLoadingMoreReviews
    val canLoadMoreReviewComments: Boolean get() = nextReviewCommentsPage != null && !isLoadingReviewComments && !isLoadingMoreReviewComments
    val canLoadMoreComments: Boolean get() = nextCommentsPage != null && !isLoadingComments && !isLoadingMoreComments
}

sealed interface PullRequestDetailAction {
    data class SelectSection(val section: PullRequestSection) : PullRequestDetailAction
    data object RetryPullRequest : PullRequestDetailAction
    data object RetryFiles : PullRequestDetailAction
    data object RetryReviews : PullRequestDetailAction
    data object RetryReviewComments : PullRequestDetailAction
    data object RetryComments : PullRequestDetailAction
    data object LoadMoreFiles : PullRequestDetailAction
    data object LoadMoreReviews : PullRequestDetailAction
    data object LoadMoreReviewComments : PullRequestDetailAction
    data object LoadMoreComments : PullRequestDetailAction
    data class ReviewBodyChanged(val body: String) : PullRequestDetailAction
    data class SubmitReview(val event: GithubReviewEvent) : PullRequestDetailAction
    data class CommentChanged(val body: String) : PullRequestDetailAction
    data object SubmitComment : PullRequestDetailAction
    data class Merge(
        val method: GithubMergeMethod,
        val commitTitle: String = "",
        val commitMessage: String = ""
    ) : PullRequestDetailAction
    data object ToggleState : PullRequestDetailAction
    data class UpdateContent(val title: String, val body: String) : PullRequestDetailAction
    data object ToggleLock : PullRequestDetailAction
    data object LoadLabels : PullRequestDetailAction
    data object LoadMoreLabels : PullRequestDetailAction
    data class UpdateLabels(val names: List<String>) : PullRequestDetailAction
    data object LoadAssignees : PullRequestDetailAction
    data object LoadMoreAssignees : PullRequestDetailAction
    data class UpdateAssignees(val logins: List<String>) : PullRequestDetailAction
    data object LoadMilestones : PullRequestDetailAction
    data object LoadMoreMilestones : PullRequestDetailAction
    data class UpdateMilestone(val number: Int?) : PullRequestDetailAction
    data class UpdateRequestedReviewers(val logins: List<String>) : PullRequestDetailAction
    data class ToggleCommentReaction(
        val commentId: Long,
        val content: GithubReactionContent
    ) : PullRequestDetailAction
}

class PullRequestDetailViewModel(
    private val owner: String,
    private val name: String,
    private val number: Int,
    private val pullRequestsRepository: GithubPullRequestsRepository,
    private val issuesRepository: GithubIssuesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PullRequestDetailUiState(owner, name, number))
    val state: StateFlow<PullRequestDetailUiState> = _state.asStateFlow()
    private var pullRequestJob: Job? = null
    private var filesJob: Job? = null
    private var reviewsJob: Job? = null
    private var reviewCommentsJob: Job? = null
    private var commentsJob: Job? = null
    private var reviewJob: Job? = null
    private var commentJob: Job? = null
    private var mergeJob: Job? = null
    private var stateJob: Job? = null
    private var contentUpdateJob: Job? = null
    private var lockJob: Job? = null
    private var labelsJob: Job? = null
    private var labelsUpdateJob: Job? = null
    private var assigneesJob: Job? = null
    private var assigneesUpdateJob: Job? = null
    private var milestonesJob: Job? = null
    private var milestoneUpdateJob: Job? = null
    private var reviewersUpdateJob: Job? = null
    private val reactionJobs = mutableMapOf<Long, Job>()

    init {
        loadPullRequest()
        loadFiles(reset = true)
        loadReviews(reset = true)
        loadReviewComments(reset = true)
        loadComments(reset = true)
    }

    fun onAction(action: PullRequestDetailAction) {
        when (action) {
            is PullRequestDetailAction.SelectSection -> _state.update { it.copy(selectedSection = action.section) }
            PullRequestDetailAction.RetryPullRequest -> loadPullRequest()
            PullRequestDetailAction.RetryFiles -> loadFiles(reset = _state.value.files.isEmpty())
            PullRequestDetailAction.RetryReviews -> loadReviews(reset = _state.value.reviews.isEmpty())
            PullRequestDetailAction.RetryReviewComments -> loadReviewComments(reset = _state.value.reviewComments.isEmpty())
            PullRequestDetailAction.RetryComments -> loadComments(reset = _state.value.comments.isEmpty())
            PullRequestDetailAction.LoadMoreFiles -> loadFiles(reset = false)
            PullRequestDetailAction.LoadMoreReviews -> loadReviews(reset = false)
            PullRequestDetailAction.LoadMoreReviewComments -> loadReviewComments(reset = false)
            PullRequestDetailAction.LoadMoreComments -> loadComments(reset = false)
            is PullRequestDetailAction.ReviewBodyChanged ->
                if (action.body.length <= GithubPullRequestReviewDraft.MAX_BODY_LENGTH) {
                    _state.update {
                        it.copy(reviewBody = action.body, reviewValidationError = false, reviewSubmitError = false)
                    }
                }
            is PullRequestDetailAction.SubmitReview -> submitReview(action.event)
            is PullRequestDetailAction.CommentChanged ->
                if (action.body.length <= GithubIssueCommentDraft.MAX_BODY_LENGTH) {
                    _state.update {
                        it.copy(commentDraft = action.body, commentValidationError = false, commentSubmitError = false)
                    }
                }
            PullRequestDetailAction.SubmitComment -> submitComment()
            is PullRequestDetailAction.Merge -> merge(
                method = action.method,
                commitTitle = action.commitTitle,
                commitMessage = action.commitMessage
            )
            PullRequestDetailAction.ToggleState -> toggleState()
            is PullRequestDetailAction.UpdateContent -> updateContent(action.title, action.body)
            PullRequestDetailAction.ToggleLock -> toggleLock()
            PullRequestDetailAction.LoadLabels -> loadLabels(reset = true)
            PullRequestDetailAction.LoadMoreLabels -> loadLabels(reset = false)
            is PullRequestDetailAction.UpdateLabels -> updateLabels(action.names)
            PullRequestDetailAction.LoadAssignees -> loadAssignees(reset = true)
            PullRequestDetailAction.LoadMoreAssignees -> loadAssignees(reset = false)
            is PullRequestDetailAction.UpdateAssignees -> updateAssignees(action.logins)
            PullRequestDetailAction.LoadMilestones -> loadMilestones(reset = true)
            PullRequestDetailAction.LoadMoreMilestones -> loadMilestones(reset = false)
            is PullRequestDetailAction.UpdateMilestone -> updateMilestone(action.number)
            is PullRequestDetailAction.UpdateRequestedReviewers -> updateRequestedReviewers(action.logins)
            is PullRequestDetailAction.ToggleCommentReaction -> toggleCommentReaction(action)
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login == _state.value.viewerLogin) return
        reactionJobs.values.forEach { it.cancel() }
        reactionJobs.clear()
        _state.update {
            it.copy(
                viewerLogin = login,
                reactionBusyCommentIds = emptySet(),
                reactionErrorCommentIds = emptySet(),
                activeReactions = emptyMap()
            )
        }
    }

    private fun loadPullRequest() {
        pullRequestJob?.cancel()
        _state.update { it.copy(isLoadingPullRequest = true, pullRequestError = false) }
        pullRequestJob = viewModelScope.launch {
            pullRequestsRepository.pullRequest(owner, name, number).fold(
                onSuccess = { pullRequest ->
                    _state.update {
                        it.copy(pullRequest = pullRequest, isLoadingPullRequest = false, pullRequestError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingPullRequest = false, pullRequestError = true) }
                }
            )
        }
    }

    private fun loadFiles(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreFiles) return
        val requestedPage = if (reset) 1 else current.nextFilesPage ?: return
        filesJob?.cancel()
        _state.update {
            it.copy(
                files = if (reset) emptyList() else it.files,
                isLoadingFiles = reset,
                isLoadingMoreFiles = !reset,
                filesError = false
            )
        }
        filesJob = viewModelScope.launch {
            pullRequestsRepository.files(owner, name, number, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            files = page.mergeItems(state.files, reset, GithubPullRequestFile::filename),
                            nextFilesPage = page.nextPage,
                            isLoadingFiles = false,
                            isLoadingMoreFiles = false,
                            filesError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingFiles = false, isLoadingMoreFiles = false, filesError = true) }
                }
            )
        }
    }

    private fun loadReviews(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreReviews) return
        val requestedPage = if (reset) 1 else current.nextReviewsPage ?: return
        reviewsJob?.cancel()
        _state.update {
            it.copy(
                reviews = if (reset) emptyList() else it.reviews,
                isLoadingReviews = reset,
                isLoadingMoreReviews = !reset,
                reviewsError = false
            )
        }
        reviewsJob = viewModelScope.launch {
            pullRequestsRepository.reviews(owner, name, number, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            reviews = page.mergeItems(state.reviews, reset, GithubPullRequestReview::id),
                            nextReviewsPage = page.nextPage,
                            isLoadingReviews = false,
                            isLoadingMoreReviews = false,
                            reviewsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(isLoadingReviews = false, isLoadingMoreReviews = false, reviewsError = true)
                    }
                }
            )
        }
    }

    private fun loadReviewComments(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreReviewComments) return
        val requestedPage = if (reset) 1 else current.nextReviewCommentsPage ?: return
        reviewCommentsJob?.cancel()
        _state.update {
            it.copy(
                reviewComments = if (reset) emptyList() else it.reviewComments,
                isLoadingReviewComments = reset,
                isLoadingMoreReviewComments = !reset,
                reviewCommentsError = false
            )
        }
        reviewCommentsJob = viewModelScope.launch {
            pullRequestsRepository.reviewComments(owner, name, number, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            reviewComments = page.mergeItems(
                                state.reviewComments,
                                reset,
                                GithubPullRequestReviewComment::id
                            ),
                            nextReviewCommentsPage = page.nextPage,
                            isLoadingReviewComments = false,
                            isLoadingMoreReviewComments = false,
                            reviewCommentsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingReviewComments = false,
                            isLoadingMoreReviewComments = false,
                            reviewCommentsError = true
                        )
                    }
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
                comments = if (reset) emptyList() else it.comments,
                isLoadingComments = reset,
                isLoadingMoreComments = !reset,
                commentsError = false
            )
        }
        commentsJob = viewModelScope.launch {
            issuesRepository.comments(owner, name, number, requestedPage).fold(
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
                        it.copy(isLoadingComments = false, isLoadingMoreComments = false, commentsError = true)
                    }
                }
            )
        }
    }

    private fun submitReview(event: GithubReviewEvent) {
        if (_state.value.isSubmittingReview) return
        val draft = GithubPullRequestReviewDraft.fromInput(event, _state.value.reviewBody).getOrElse {
            _state.update { it.copy(reviewValidationError = true, reviewSubmitError = false) }
            return
        }
        _state.update {
            it.copy(isSubmittingReview = true, reviewValidationError = false, reviewSubmitError = false)
        }
        reviewJob?.cancel()
        reviewJob = viewModelScope.launch {
            pullRequestsRepository.submitReview(owner, name, number, draft).fold(
                onSuccess = { review ->
                    _state.update {
                        it.copy(
                            reviews = (it.reviews + review).distinctBy(GithubPullRequestReview::id),
                            reviewBody = "",
                            isSubmittingReview = false,
                            reviewSubmitError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isSubmittingReview = false, reviewSubmitError = true) }
                }
            )
        }
    }

    private fun submitComment() {
        if (_state.value.isSubmittingComment || _state.value.pullRequest?.isLocked == true) return
        val draft = GithubIssueCommentDraft.fromInput(_state.value.commentDraft).getOrElse {
            _state.update { it.copy(commentValidationError = true, commentSubmitError = false) }
            return
        }
        _state.update {
            it.copy(isSubmittingComment = true, commentValidationError = false, commentSubmitError = false)
        }
        commentJob?.cancel()
        commentJob = viewModelScope.launch {
            issuesRepository.addComment(owner, name, number, draft).fold(
                onSuccess = { comment ->
                    _state.update {
                        it.copy(
                            comments = (it.comments + comment).distinctBy(GithubIssueComment::id),
                            commentDraft = "",
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

    private fun merge(method: GithubMergeMethod, commitTitle: String, commitMessage: String) {
        val pullRequest = _state.value.pullRequest ?: return
        if (_state.value.isMerging || pullRequest.isMerged || pullRequest.state == GithubPullRequestState.CLOSED) return
        val draft = GithubMergeDraft.fromInput(
            method = method,
            expectedHeadSha = pullRequest.head.sha,
            commitTitle = commitTitle,
            commitMessage = commitMessage
        ).getOrElse {
            _state.update {
                it.copy(mergeValidationError = true, mergeError = false, mergeResult = null)
            }
            return
        }
        _state.update {
            it.copy(
                isMerging = true,
                mergeValidationError = false,
                mergeError = false,
                mergeResult = null
            )
        }
        mergeJob?.cancel()
        mergeJob = viewModelScope.launch {
            pullRequestsRepository.merge(owner, name, number, draft).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = if (result.merged) {
                                state.pullRequest?.copy(isMerged = true, state = GithubPullRequestState.CLOSED)
                            } else {
                                state.pullRequest
                            },
                            isMerging = false,
                            mergeError = !result.merged,
                            mergeResult = result
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isMerging = false, mergeError = true) }
                }
            )
        }
    }

    private fun toggleState() {
        val current = _state.value.pullRequest ?: return
        if (_state.value.isUpdatingState || current.isMerged) return
        val target = if (current.state == GithubPullRequestState.OPEN) {
            GithubPullRequestState.CLOSED
        } else {
            GithubPullRequestState.OPEN
        }
        _state.update { it.copy(isUpdatingState = true, stateUpdateError = false) }
        stateJob?.cancel()
        stateJob = viewModelScope.launch {
            pullRequestsRepository.updateState(owner, name, number, target).fold(
                onSuccess = { pullRequest ->
                    _state.update {
                        it.copy(pullRequest = pullRequest, isUpdatingState = false, stateUpdateError = false)
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingState = false, stateUpdateError = true) }
                }
            )
        }
    }

    private fun updateContent(title: String, body: String) {
        if (_state.value.isUpdatingContent) return
        val draft = GithubPullRequestDraft.fromInput(title, body).getOrElse {
            _state.update {
                it.copy(contentValidationError = true, contentUpdateError = false)
            }
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
        contentUpdateJob = viewModelScope.launch {
            pullRequestsRepository.updateContent(owner, name, number, draft).fold(
                onSuccess = { pullRequest ->
                    _state.update {
                        it.copy(
                            pullRequest = pullRequest,
                            isUpdatingContent = false,
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

    private fun toggleLock() {
        val current = _state.value.pullRequest ?: return
        if (_state.value.isUpdatingLock) return
        val target = !current.isLocked
        _state.update { it.copy(isUpdatingLock = true, lockUpdateError = false) }
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            issuesRepository.updateIssueLock(owner, name, number, target).fold(
                onSuccess = { issue ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = state.pullRequest?.copy(isLocked = issue.isLocked),
                            isUpdatingLock = false,
                            lockUpdateError = false
                        )
                    }
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
                labelsError = false
            )
        }
        labelsJob = viewModelScope.launch {
            issuesRepository.labels(owner, name, page = page).fold(
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
        if (_state.value.isUpdatingLabels) return
        _state.update { it.copy(isUpdatingLabels = true, labelsUpdateError = false) }
        labelsUpdateJob?.cancel()
        labelsUpdateJob = viewModelScope.launch {
            issuesRepository.updateIssueLabels(owner, name, number, names).fold(
                onSuccess = { issue ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = state.pullRequest?.copy(labels = issue.labels),
                            isUpdatingLabels = false,
                            labelsUpdateError = false
                        )
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
        if (!reset && (
            current.nextAssigneesPage == null || current.isLoadingAssignees || current.isLoadingMoreAssignees
        )) return
        val page = if (reset) 1 else current.nextAssigneesPage ?: return
        assigneesJob?.cancel()
        _state.update {
            it.copy(
                availableAssignees = if (reset) emptyList() else it.availableAssignees,
                isLoadingAssignees = reset,
                isLoadingMoreAssignees = !reset,
                assigneesError = false
            )
        }
        assigneesJob = viewModelScope.launch {
            issuesRepository.assignees(owner, name, page = page).fold(
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
        if (_state.value.isUpdatingAssignees) return
        _state.update { it.copy(isUpdatingAssignees = true, assigneesUpdateError = false) }
        assigneesUpdateJob?.cancel()
        assigneesUpdateJob = viewModelScope.launch {
            issuesRepository.updateIssueAssignees(owner, name, number, logins).fold(
                onSuccess = { issue ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = state.pullRequest?.copy(assignees = issue.assignees),
                            isUpdatingAssignees = false,
                            assigneesUpdateError = false
                        )
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
        if (!reset && (
            current.nextMilestonesPage == null || current.isLoadingMilestones || current.isLoadingMoreMilestones
        )) return
        val page = if (reset) 1 else current.nextMilestonesPage ?: return
        milestonesJob?.cancel()
        _state.update {
            it.copy(
                availableMilestones = if (reset) emptyList() else it.availableMilestones,
                isLoadingMilestones = reset,
                isLoadingMoreMilestones = !reset,
                milestonesError = false
            )
        }
        milestonesJob = viewModelScope.launch {
            issuesRepository.milestones(owner, name, page = page).fold(
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
        if (_state.value.isUpdatingMilestone) return
        _state.update { it.copy(isUpdatingMilestone = true, milestoneUpdateError = false) }
        milestoneUpdateJob?.cancel()
        milestoneUpdateJob = viewModelScope.launch {
            issuesRepository.updateIssueMilestone(owner, name, this@PullRequestDetailViewModel.number, number).fold(
                onSuccess = { issue ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = state.pullRequest?.copy(milestone = issue.milestone),
                            isUpdatingMilestone = false,
                            milestoneUpdateError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingMilestone = false, milestoneUpdateError = true) }
                }
            )
        }
    }

    private fun updateRequestedReviewers(logins: List<String>) {
        val pullRequest = _state.value.pullRequest ?: return
        if (_state.value.isUpdatingReviewers) return
        val update = GithubRequestedReviewersUpdate.fromInput(
            current = pullRequest.requestedReviewers.map(GithubUserSummary::login),
            requested = logins
        ).getOrElse {
            _state.update {
                it.copy(reviewersValidationError = true, reviewersUpdateError = false)
            }
            return
        }
        if (update.requested.any { it.equals(pullRequest.author.login, ignoreCase = true) }) {
            _state.update {
                it.copy(reviewersValidationError = true, reviewersUpdateError = false)
            }
            return
        }
        _state.update {
            it.copy(
                isUpdatingReviewers = true,
                reviewersValidationError = false,
                reviewersUpdateError = false
            )
        }
        reviewersUpdateJob?.cancel()
        reviewersUpdateJob = viewModelScope.launch {
            pullRequestsRepository.updateRequestedReviewers(owner, name, number, update).fold(
                onSuccess = { updated ->
                    _state.update { state ->
                        state.copy(
                            pullRequest = state.pullRequest?.copy(
                                requestedReviewers = updated.requestedReviewers
                            ),
                            isUpdatingReviewers = false,
                            reviewersUpdateError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingReviewers = false, reviewersUpdateError = true) }
                }
            )
        }
    }

    private fun toggleCommentReaction(action: PullRequestDetailAction.ToggleCommentReaction) {
        val viewerLogin = _state.value.viewerLogin ?: return
        if (action.commentId in _state.value.reactionBusyCommentIds) return
        _state.update {
            it.copy(
                reactionBusyCommentIds = it.reactionBusyCommentIds + action.commentId,
                reactionErrorCommentIds = it.reactionErrorCommentIds - action.commentId
            )
        }
        reactionJobs[action.commentId]?.cancel()
        reactionJobs[action.commentId] = viewModelScope.launch {
            issuesRepository.toggleCommentReaction(
                owner = owner,
                name = name,
                commentId = action.commentId,
                content = action.content,
                viewerLogin = viewerLogin
            ).fold(
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
                            } else comment
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
                            activeReactions = state.activeReactions + (action.commentId to updatedContentSet)
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

    class Factory(
        private val owner: String,
        private val name: String,
        private val number: Int,
        private val pullRequestsRepository: GithubPullRequestsRepository,
        private val issuesRepository: GithubIssuesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PullRequestDetailViewModel::class.java))
            return PullRequestDetailViewModel(
                owner,
                name,
                number,
                pullRequestsRepository,
                issuesRepository
            ) as T
        }
    }
}
