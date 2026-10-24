package takagi.ru.monica.debug

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.repository.*

/** Uses the production screen and ViewModel; every write stays in memory. */
@Composable
internal fun RepositorySafetySample(failWrites: Boolean = false, onBack: () -> Unit, report: (String) -> Unit) {
    val repository = remember { SafetyRepository(failWrites, report) }
    val actions = remember {
        object : GithubRepositoryActionsRepository {
            override suspend fun viewerState(owner: String, name: String) = Result.success(GithubRepositoryViewerState(false, false))
            override suspend fun setStarred(owner: String, name: String, starred: Boolean) = Result.success(starred)
            override suspend fun setWatching(owner: String, name: String, watching: Boolean) = Result.success(watching)
            override suspend fun fork(owner: String, name: String) = Result.failure<GithubRepository>(UnsupportedOperationException())
        }
    }
    val model: RepositoryDetailViewModel = viewModel(factory = RepositoryDetailViewModel.Factory("sample", "android-client", repository, actions))
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.onSessionChanged(GithubSession.SignedIn(sampleAccount)) }
    var moreOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = moreOpen) { moreOpen = false }
    if (moreOpen) {
        RepositoryMoreScreen(
            state = state, onAction = model::onAction, onBack = { moreOpen = false },
            onOpenBranches = {}, onOpenTags = {}, onOpenCollaborators = {}, onOpenWebhooks = {},
            onOpenActions = {}, onOpenDiscussions = {}, onOpenCommits = {}, isSignedIn = true, onSignIn = {}, onOpenExternal = report
        )
    } else {
        RepositoryDetailScreen(
            state = state, onAction = model::onAction, onBack = onBack,
            onBrowseCode = {}, onOpenIssues = {}, onOpenPullRequests = {}, onOpenReleases = {},
            onOpenMore = { moreOpen = true }, isSignedIn = true, onSignIn = {},
            onOpenRepository = {}, onOpenExternal = report
        )
    }
}

private class SafetyRepository(private val failWrites: Boolean, private val report: (String) -> Unit) : GithubRepositoryDetailsRepository {
    private var snapshot = GithubRepositoryDetails(
        GithubRepository(1, "android-client", "sample/android-client", "Local repository management fixture", "Kotlin", 128, null, false, "https://example.invalid/sample/android-client"),
        "sample", null, "main", 12, 34, 3, "MIT", listOf("android", "github"), false, false, GithubCollaboratorRole.ADMIN
    )
    override suspend fun details(owner: String, name: String) = Result.success(snapshot)
    override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>("# Repository settings\n\nAdministration is available from More. All writes in this sample stay on the device.")
    override suspend fun branchProtection(owner: String, name: String, branch: String) = Result.success<GithubBranchProtection?>(null)
    override suspend fun updateTopics(owner: String, name: String, topics: List<String>) = Result.success(topics)
    override suspend fun updateSettings(owner: String, name: String, edit: GithubRepositorySettingsEdit): Result<GithubRepositorySettings> {
        report("Settings request: $edit")
        delay(300)
        if (failWrites) return Result.failure(GithubApiException(403))
        if (edit.defaultBranch != null && edit.defaultBranch !in listOf("main", "develop")) return Result.failure(GithubApiException(422))
        snapshot = snapshot.copy(
            repository = snapshot.repository.copy(isPrivate = edit.isPrivate ?: snapshot.repository.isPrivate, description = edit.description ?: snapshot.repository.description),
            isArchived = edit.isArchived ?: snapshot.isArchived,
            defaultBranch = edit.defaultBranch ?: snapshot.defaultBranch,
            features = snapshot.features.copy(hasIssues = edit.hasIssues ?: snapshot.features.hasIssues, hasWiki = edit.hasWiki ?: snapshot.features.hasWiki, hasProjects = edit.hasProjects ?: snapshot.features.hasProjects)
        )
        return Result.success(GithubRepositorySettings(snapshot.repository.isPrivate, snapshot.isArchived, snapshot.features, snapshot.repository.description, snapshot.defaultBranch))
    }
    override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
    override suspend fun setCollaborator(owner: String, name: String, invite: GithubCollaboratorInvite) = Result.failure<GithubCollaboratorChange>(UnsupportedOperationException())
    override suspend fun removeCollaborator(owner: String, name: String, login: String) = Result.failure<Unit>(UnsupportedOperationException())
    override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
    override suspend fun updateWebhook(owner: String, name: String, id: Long, edit: GithubWebhookEdit) = Result.failure<GithubRepositoryWebhook>(UnsupportedOperationException())
    override suspend fun deleteWebhook(owner: String, name: String, id: Long) = Result.failure<Unit>(UnsupportedOperationException())
    override suspend fun webhookDeliveries(owner: String, name: String, id: Long, cursor: String?, perPage: Int) = Result.success(GithubWebhookDeliveriesPage(emptyList(), null))
    override suspend fun redeliverWebhook(owner: String, name: String, id: Long, deliveryId: Long) = Result.failure<Unit>(UnsupportedOperationException())
}
