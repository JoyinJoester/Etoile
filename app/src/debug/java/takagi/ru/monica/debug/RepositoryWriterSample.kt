package takagi.ru.monica.debug

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.repository.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun RepositoryWriterSample() {
    val repository = remember {
        object : GithubRepositoryContentsRepository {
            var text = "fun render(): String = \"before\""
            var blobSha = "a".repeat(40)
            override suspend fun file(owner: String, name: String, path: String, ref: String?) =
                Result.success(GithubFileContent.Text(text, blobSha))
            override suspend fun write(owner: String, name: String, change: GithubFileWrite): Result<GithubFileWriteResult> {
                if (change.content == "force failure") return Result.failure(java.io.IOException("offline"))
                // Matches the real API: the optimistic-lock sha no longer points at the stored blob.
                if (change.content == "force conflict") return Result.failure(GithubApiException(409))
                text = change.content.orEmpty()
                blobSha = "b".repeat(40)
                return Result.success(GithubFileWriteResult("device-commit-123456", blobSha))
            }
            override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubBranch>(emptyList(), null))
            override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubTag>(emptyList(), null))
            override suspend fun directory(owner: String, name: String, path: String, ref: String?) = Result.success(emptyList<GithubContentItem>())
        }
    }
    val details = remember {
        object : GithubRepositoryDetailsRepository {
            override suspend fun details(owner: String, name: String) = Result.success(
                GithubRepositoryDetails(
                    repository = GithubRepository(1, "repo", "sample/repo", null, null, 0, null, false, "https://github.com/sample/repo"),
                    ownerLogin = "sample", ownerAvatarUrl = null, defaultBranch = "main",
                    forks = 0, watchers = 0, openIssues = 0, license = null, topics = emptyList(),
                    isArchived = false, isFork = false, viewerRole = GithubCollaboratorRole.ADMIN
                )
            )
            override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)
            override suspend fun branchProtection(owner: String, name: String, branch: String) = Result.success<GithubBranchProtection?>(null)
            override suspend fun updateTopics(owner: String, name: String, topics: List<String>) = Result.success(topics)
            override suspend fun updateSettings(owner: String, name: String, edit: GithubRepositorySettingsEdit) =
                Result.success(
                    GithubRepositorySettings(
                        isPrivate = edit.isPrivate == true,
                        isArchived = edit.isArchived == true,
                        description = edit.description,
                        features = GithubRepositoryFeatures(
                            hasIssues = edit.hasIssues != false,
                            hasWiki = edit.hasWiki != false,
                            hasProjects = edit.hasProjects != false
                        )
                    )
                )
            override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
                Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
            override suspend fun setCollaborator(
                owner: String,
                name: String,
                invite: GithubCollaboratorInvite
            ) = Result.failure<GithubCollaboratorChange>(UnsupportedOperationException("this sample never invites"))
            override suspend fun removeCollaborator(owner: String, name: String, login: String) =
                Result.failure<Unit>(UnsupportedOperationException("this sample never removes"))
            override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
                Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
            override suspend fun webhookDeliveries(
                owner: String, name: String, id: Long, page: Int, perPage: Int
            ): Result<GithubPage<GithubWebhookDelivery>> =
                Result.failure(UnsupportedOperationException("this sample never lists deliveries"))
            override suspend fun redeliverWebhook(owner: String, name: String, id: Long, deliveryId: Long): Result<Unit> =
                Result.failure(UnsupportedOperationException("this sample never redelivers"))
            override suspend fun updateWebhook(
                owner: String, name: String, id: Long, edit: GithubWebhookEdit
            ): Result<GithubRepositoryWebhook> = Result.failure(UnsupportedOperationException())
            override suspend fun deleteWebhook(owner: String, name: String, id: Long): Result<Unit> =
                Result.failure(UnsupportedOperationException())
        }
    }
    val factory = remember(repository, details) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            RepositoryFileViewModel("sample", "repo", "main", "src/Main.kt", repository, details) as T
    } }
    val model: RepositoryFileViewModel = viewModel(key = "writer-sample", factory = factory)
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.onSessionChanged(GithubSession.SignedIn(sampleAccount)) }
    RepositoryFileScreen(state, model::onAction, {}, {}, modifier = androidx.compose.ui.Modifier.fillMaxSize())
}
