package takagi.ru.monica.github.domain

enum class GithubListSort { CREATED, UPDATED }

enum class GithubSortDirection { ASC, DESC }

data class GithubIssueListQuery(
    val state: GithubIssueState,
    val sort: GithubListSort = GithubListSort.UPDATED,
    val direction: GithubSortDirection = GithubSortDirection.DESC
)

data class GithubPullRequestListQuery(
    val state: GithubPullRequestState,
    val sort: GithubListSort = GithubListSort.UPDATED,
    val direction: GithubSortDirection = GithubSortDirection.DESC
)
