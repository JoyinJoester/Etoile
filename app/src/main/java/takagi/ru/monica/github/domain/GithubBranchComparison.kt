package takagi.ru.monica.github.domain

data class GithubBranchComparison(
    val status: String,
    val aheadBy: Int,
    val behindBy: Int,
    val totalCommits: Int,
    val files: List<GithubPullRequestFile>,
    // GitHub's compare endpoint returns at most 300 files, only on the first page.
    val fileLimitReached: Boolean
)
