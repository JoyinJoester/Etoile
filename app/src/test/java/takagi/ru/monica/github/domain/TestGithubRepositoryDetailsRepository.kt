package takagi.ru.monica.github.domain

class TestGithubRepositoryDetailsRepository(
    var viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
) : GithubRepositoryDetailsRepository {
    var detailsRequests = 0
        private set

    override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> {
        detailsRequests++
        return Result.success(details(owner, name, viewerRole))
    }

    override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)

    override suspend fun branchProtection(owner: String, name: String, branch: String) =
        Result.success<GithubBranchProtection?>(null)

    override suspend fun updateTopics(owner: String, name: String, topics: List<String>) =
        Result.success(topics)

    override suspend fun updateSettings(owner: String, name: String, edit: GithubRepositorySettingsEdit) =
        Result.success(
            GithubRepositorySettings(
                isPrivate = edit.isPrivate == true,
                isArchived = edit.isArchived == true
            )
        )

    override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
        Result.success(GithubPage<GithubCollaborator>(emptyList(), null))

    var inviteResult: Result<GithubCollaboratorChange> =
        Result.success(GithubCollaboratorChange.Invited)

    val invites = mutableListOf<GithubCollaboratorInvite>()
    val removals = mutableListOf<String>()

    override suspend fun setCollaborator(
        owner: String,
        name: String,
        invite: GithubCollaboratorInvite
    ): Result<GithubCollaboratorChange> {
        invites += invite
        return inviteResult
    }

    override suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit> {
        removals += login
        return Result.success(Unit)
    }

    override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
        Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
    override suspend fun updateWebhook(
        owner: String, name: String, id: Long, edit: GithubWebhookEdit
    ): Result<GithubRepositoryWebhook> = Result.failure(UnsupportedOperationException())
    override suspend fun deleteWebhook(owner: String, name: String, id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    companion object {
        fun details(
            owner: String = "openai",
            name: String = "codex",
            viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
        ) = GithubRepositoryDetails(
            repository = GithubRepository(
                id = 1,
                name = name,
                fullName = "$owner/$name",
                description = null,
                language = null,
                stars = 0,
                updatedAt = null,
                isPrivate = false,
                htmlUrl = "https://github.com/$owner/$name"
            ),
            ownerLogin = owner,
            ownerAvatarUrl = null,
            defaultBranch = "main",
            forks = 0,
            watchers = 0,
            openIssues = 0,
            license = null,
            topics = emptyList(),
            isArchived = false,
            isFork = false,
            viewerRole = viewerRole
        )
    }
}
