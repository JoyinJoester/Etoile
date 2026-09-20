package takagi.ru.monica.debug

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.pullrequest.*

@Composable
internal fun CreatePullRequestSample() {
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                CreatePullRequestViewModel(
                    { Result.success(GithubPage(listOf(GithubBranch("main", "base", false), GithubBranch("feature/mobile", "head", false)), null)) },
                    { input ->
                        Result.success(GithubPullRequest(
                            id = 10, number = 10, title = input.title, body = input.body,
                            state = GithubPullRequestState.OPEN, isDraft = input.draft, isMerged = false,
                            mergeable = null, mergeableState = null, author = GithubUserSummary("sample", null, "https://github.com/sample"),
                            labels = emptyList(), assignees = emptyList(), requestedReviewers = emptyList(),
                            head = GithubPullRequestRef(input.head, input.head, "head", "sample/repo"),
                            base = GithubPullRequestRef(input.base, input.base, "base", "sample/repo"),
                            comments = 0, reviewComments = 0, commits = 1, additions = 1, deletions = 0, changedFiles = 1,
                            createdAt = "", updatedAt = "", closedAt = null, mergedAt = null, htmlUrl = "https://github.com/sample/repo/pull/10"
                        ))
                    }, extras.createSavedStateHandle(),
                    { _, head, _ ->
                        if (head == "missing") Result.failure(java.io.IOException("Fixture comparison failure"))
                        else if (head == "main") Result.success(GithubBranchComparison("behind", 0, 1, 0, emptyList(), false))
                        else Result.success(GithubBranchComparison("ahead", 1, 0, 1,
                            (1..6).map { index -> GithubPullRequestFile(
                                sha = "file$index", filename = "src/Feature$index.kt", status = "modified",
                                additions = 1, deletions = 1, changes = 2,
                                patch = "@@ -1 +1 @@\n-oldValue$index\n+newValue$index",
                                blobUrl = "https://github.com/sample/repo/blob/head/src/Feature$index.kt", rawUrl = null
                            ) }, false))
                    }, { Result.success(listOf(GithubPullRequestTemplate("sample/repo", ".github/PULL_REQUEST_TEMPLATE.md", "## Summary\n\n## Testing\n"))) }) as T
        }
    }
    val vm: CreatePullRequestViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    val created = state.created
    if (created != null) Column(Modifier.systemBarsPadding()) {
        Text("Created #${created.number}: ${created.title}")
        Text("${created.head.ref} → ${created.base.ref}")
        Text("Draft: ${created.isDraft}")
    } else CreatePullRequestScreen("sample/repo", state, vm::edit, vm::submit, vm::loadBranches, {}, vm::preview,
        onLoadTemplates = vm::loadTemplates, onApplyTemplate = vm::applyTemplate)
}
