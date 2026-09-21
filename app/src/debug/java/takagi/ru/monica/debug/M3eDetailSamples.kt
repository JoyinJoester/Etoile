package takagi.ru.monica.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import takagi.ru.monica.github.domain.GithubActionsConclusion
import takagi.ru.monica.github.domain.GithubActionsLog
import takagi.ru.monica.github.domain.GithubActionsStatus
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestRef
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowStep
import takagi.ru.monica.github.feature.actions.ActionsJobDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsJobDetailUiState
import takagi.ru.monica.github.feature.actions.ActionsRunDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsRunDetailUiState
import takagi.ru.monica.github.feature.pullrequest.PullRequestDetailAction
import takagi.ru.monica.github.feature.pullrequest.PullRequestDetailScreen
import takagi.ru.monica.github.feature.pullrequest.PullRequestDetailUiState
import takagi.ru.monica.github.feature.pullrequest.PullRequestSection

/** Complete production detail screens; all interactions stay in local sample state. */
@Composable
internal fun M3eDetailSamples(
    page: String,
    onBack: () -> Unit,
    onOpenJob: () -> Unit,
    onReport: (String) -> Unit
) {
    var section by rememberSaveable { mutableStateOf(PullRequestSection.FILES) }
    var reviewBody by rememberSaveable { mutableStateOf("") }
    var commentDraft by rememberSaveable { mutableStateOf("") }
    when (page) {
        "pull-request" -> PullRequestDetailScreen(
            state = PullRequestDetailUiState(
                owner = "etoile", name = "android-client", number = 128,
                pullRequest = detailPullRequest, files = detailFiles,
                selectedSection = section, isLoadingPullRequest = false,
                isLoadingFiles = false, isLoadingReviews = false,
                isLoadingReviewComments = false, isLoadingComments = false,
                reviewBody = reviewBody, commentDraft = commentDraft,
                viewerLogin = "layout-reviewer"
            ),
            isSignedIn = true,
            onAction = { action ->
                when (action) {
                    is PullRequestDetailAction.SelectSection -> section = action.section
                    is PullRequestDetailAction.ReviewBodyChanged -> reviewBody = action.body
                    is PullRequestDetailAction.CommentChanged -> commentDraft = action.body
                    else -> onReport("Pull request: $action")
                }
            },
            onBack = onBack,
            onSignIn = { onReport("Sign in") },
            onOpenExternal = onReport
        )
        "actions-run" -> ActionsRunDetailScreen(
            state = ActionsRunDetailUiState(
                owner = "etoile", name = "android-client", runId = detailRun.id,
                run = detailRun, jobs = listOf(detailJob),
                isLoadingRun = false, isLoadingJobs = false
            ),
            onAction = { onReport("Run: $it") },
            onBack = onBack,
            onOpenJob = { onOpenJob() },
            onOpenExternal = onReport,
            enableArtifactDownloads = false
        )
        "actions-job" -> ActionsJobDetailScreen(
            state = ActionsJobDetailUiState(
                owner = "etoile", name = "android-client", jobId = detailJob.id,
                job = detailJob,
                log = detailLog,
                isLoadingJob = false, isLoadingLog = false
            ),
            onAction = { onReport("Job: $it") },
            onBack = onBack,
            onOpenExternal = onReport
        )
    }
}

private const val detailTimestamp = "2026-09-17T00:00:00Z"
private val detailAuthor = GithubUserSummary(
    "android-release-maintainer", null, "https://example.invalid/maintainer"
)
internal val detailPullRequest = GithubPullRequest(
    id = 128, number = 128,
    title = "Keep pull request reviews and build logs readable at larger text sizes",
    body = "## Adaptive detail pages\n\nUse the available space for changes and review activity. " +
        "When text grows, return to a single reading column so actions stay reachable.",
    state = GithubPullRequestState.OPEN, isDraft = false, isMerged = false,
    mergeable = true, mergeableState = "clean", author = detailAuthor,
    labels = emptyList(), assignees = emptyList(), requestedReviewers = emptyList(),
    head = GithubPullRequestRef("etoile:feature/adaptive-detail-reading", "feature/adaptive-detail-reading", "a".repeat(40), "etoile/android-client"),
    base = GithubPullRequestRef("etoile:main", "main", "b".repeat(40), "etoile/android-client"),
    comments = 0, reviewComments = 0, commits = 3, additions = 48, deletions = 12,
    changedFiles = 3, createdAt = detailTimestamp, updatedAt = detailTimestamp,
    closedAt = null, mergedAt = null,
    htmlUrl = "https://example.invalid/etoile/android-client/pull/128"
)
private val detailFiles = listOf("ActionsScreens.kt", "PullRequestsScreens.kt", "GithubDetailLayout.kt").mapIndexed { index, filename ->
    GithubPullRequestFile(
        sha = "file-$index", filename = "app/src/main/java/etoile/github/detail/$filename",
        status = "modified", additions = 16, deletions = 4, changes = 20,
        patch = "@@ -20,6 +20,6 @@\n- val expanded = width >= breakpoint\n" +
            "+ val expanded = width >= breakpoint * fontScale\n" +
            "+ val summaryWidth = 380.dp * fontScale\n" +
            "  DetailPage(expanded = expanded, summaryWidth = summaryWidth)",
        blobUrl = "https://example.invalid/etoile/android-client/blob/main/$filename",
        rawUrl = null
    )
}
internal val detailRun = GithubWorkflowRun(
    id = 108, workflowId = 7, name = "Android Build",
    displayTitle = "Verify adaptive details, large text and readable workflow logs",
    runNumber = 108, event = "pull_request", status = GithubActionsStatus.COMPLETED,
    conclusion = GithubActionsConclusion.SUCCESS,
    headBranch = "feature/adaptive-detail-reading", headSha = "a".repeat(40),
    actor = detailAuthor, createdAt = detailTimestamp, updatedAt = detailTimestamp,
    runStartedAt = detailTimestamp,
    htmlUrl = "https://example.invalid/etoile/android-client/actions/runs/108"
)
private val detailJob = GithubWorkflowJob(
    id = 218, runId = 108, name = "Build Android preview and verify layouts",
    status = GithubActionsStatus.COMPLETED, conclusion = GithubActionsConclusion.SUCCESS,
    startedAt = detailTimestamp, completedAt = "2026-09-17T00:03:00Z",
    htmlUrl = "https://example.invalid/etoile/android-client/actions/runs/108/job/218",
    runnerName = "android-release-runner-arm64", labels = listOf("ubuntu-latest", "arm64"),
    steps = listOf("Check out source", "Compile Android application", "Verify unit tests and layouts").mapIndexed { index, name ->
        GithubWorkflowStep(index + 1, name, GithubActionsStatus.COMPLETED,
            GithubActionsConclusion.SUCCESS, detailTimestamp, "2026-09-17T00:03:00Z")
    }
)
private val detailLog = GithubActionsLog(
    text = (1..18).joinToString("\n") { line ->
        "> Task :app:verifyLayout$line  Keep long build output readable when the system text size changes."
    } + "\nBUILD SUCCESSFUL",
    isTruncated = false
)
