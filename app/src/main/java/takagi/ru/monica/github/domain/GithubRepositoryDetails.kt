package takagi.ru.monica.github.domain

data class GithubRepositoryDetails(
    val repository: GithubRepository,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val defaultBranch: String,
    val forks: Int,
    val watchers: Int,
    val openIssues: Int,
    val license: String?,
    val topics: List<String>,
    val isArchived: Boolean,
    val isFork: Boolean,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN,
    val features: GithubRepositoryFeatures = GithubRepositoryFeatures()
)

data class GithubBranchProtection(
    val branch: String,
    val requiredStatusChecks: Int,
    val requiredApprovingReviews: Int?,
    val enforceAdmins: Boolean
)

data class GithubRepositorySettings(
    val isPrivate: Boolean,
    val isArchived: Boolean,
    val features: GithubRepositoryFeatures = GithubRepositoryFeatures(),
    val description: String? = null,
    val defaultBranch: String = "main"
)

data class GithubRepositorySettingsEdit(
    val isPrivate: Boolean? = null,
    val isArchived: Boolean? = null,
    val hasIssues: Boolean? = null,
    val hasWiki: Boolean? = null,
    val hasProjects: Boolean? = null,
    // null leaves the description alone while an empty string clears it on purpose.
    val description: String? = null,
    val defaultBranch: String? = null
)

// Discussions is absent on purpose: GitHub documents `has_discussions` only for the create endpoints, not for this patch.
data class GithubRepositoryFeatures(
    val hasIssues: Boolean = true,
    val hasWiki: Boolean = true,
    val hasProjects: Boolean = true
)

enum class GithubRepositoryFeature {
    Issues,
    Wiki,
    Projects
}

enum class GithubCollaboratorRole {
    READ, TRIAGE, WRITE, MAINTAIN, ADMIN, UNKNOWN;

    val canTriage: Boolean
        get() = this == TRIAGE || canPush

    val canPush: Boolean
        get() = this == WRITE || this == MAINTAIN || this == ADMIN

    val canMaintain: Boolean
        get() = this == MAINTAIN || this == ADMIN

    val canAdmin: Boolean
        get() = this == ADMIN

    /** The value the collaborators endpoint names this role with; UNKNOWN is read-only here. */
    val apiPermission: String?
        get() = when (this) {
            READ -> "pull"
            TRIAGE -> "triage"
            WRITE -> "push"
            MAINTAIN -> "maintain"
            ADMIN -> "admin"
            UNKNOWN -> null
        }

    companion object {
        /** Roles the invite form can hand out, in the order the picker lists them. */
        val assignable: List<GithubCollaboratorRole>
            get() = listOf(READ, TRIAGE, WRITE, MAINTAIN, ADMIN)
    }
}

/** What the collaborator form wants to send, already reduced to what the API accepts. */
class GithubCollaboratorInvite private constructor(
    val login: String,
    val role: GithubCollaboratorRole,
    val permission: String
) {
    companion object {
        private const val MAX_LOGIN_LENGTH = 39

        // Alphanumerics and single hyphens, starting and ending with an alphanumeric.
        private val LOGIN_PATTERN = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9]))*$")

        fun isValidLogin(login: String): Boolean {
            val trimmed = login.trim()
            return trimmed.length <= MAX_LOGIN_LENGTH && LOGIN_PATTERN.matches(trimmed)
        }

        fun fromInput(login: String, role: GithubCollaboratorRole) =
            runCatching<GithubCollaboratorInvite> {
                require(isValidLogin(login))
                GithubCollaboratorInvite(
                    login = login.trim(),
                    role = role,
                    permission = requireNotNull(role.apiPermission)
                )
            }
    }
}

/** Whether the write sent someone a fresh invitation or restated an existing collaborator's role. */
enum class GithubCollaboratorChange {
    Invited,
    Updated
}

data class GithubCollaborator(
    val user: GithubUserSummary,
    val role: GithubCollaboratorRole,
    /** Total commits contributed when the entry came from the public /contributors fallback. */
    val contributions: Int? = null
) {
    val isContributor: Boolean get() = contributions != null && role == GithubCollaboratorRole.UNKNOWN
}

data class GithubRepositoryWebhook(
    val id: Long,
    val name: String,
    val url: String? = null,
    val isActive: Boolean,
    val events: List<String>,
    val lastResponseCode: Int?,
    val lastResponseStatus: String?,
    val lastResponseMessage: String?
)

// A null field means "leave this hook property alone", matching the conditional PATCH payload.
data class GithubWebhookEdit(
    val active: Boolean? = null
)

interface GithubRepositoryDetailsRepository {
    suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails>
    suspend fun readme(owner: String, name: String, ref: String? = null): Result<String?>
    suspend fun branchProtection(owner: String, name: String, branch: String): Result<GithubBranchProtection?>
    suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>>
    suspend fun updateSettings(
        owner: String,
        name: String,
        edit: GithubRepositorySettingsEdit
    ): Result<GithubRepositorySettings>
    suspend fun collaborators(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubCollaborator>>
    // The same PUT both invites a newcomer and restates an existing collaborator's role.
    suspend fun setCollaborator(
        owner: String,
        name: String,
        invite: GithubCollaboratorInvite
    ): Result<GithubCollaboratorChange>
    suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit>
    suspend fun webhooks(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRepositoryWebhook>>
    suspend fun updateWebhook(
        owner: String,
        name: String,
        id: Long,
        edit: GithubWebhookEdit
    ): Result<GithubRepositoryWebhook>
    suspend fun deleteWebhook(owner: String, name: String, id: Long): Result<Unit>
}
