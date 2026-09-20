package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryFeatures
import takagi.ru.monica.github.domain.GithubRepositorySettings

@Serializable
data class GithubRepositoryDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("html_url") val htmlUrl: String,
    val owner: GithubRepositoryOwnerDto? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("subscribers_count") val watchers: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    val license: GithubRepositoryLicenseDto? = null,
    val topics: List<String> = emptyList(),
    val archived: Boolean = false,
    val fork: Boolean = false,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_wiki") val hasWiki: Boolean = true,
    @SerialName("has_projects") val hasProjects: Boolean = true,
    val permissions: GithubRepositoryPermissionsDto? = null
) {
    // GitHub echoes an empty string once a description is cleared; "no description" is the same state.
    fun toDomain() = GithubRepository(
        id, name, fullName, description?.takeIf(String::isNotBlank), language, stars, updatedAt, isPrivate, htmlUrl
    )

    fun toFeatures() = GithubRepositoryFeatures(hasIssues, hasWiki, hasProjects)

    fun toSettings() = GithubRepositorySettings(
        isPrivate = isPrivate,
        isArchived = archived,
        features = toFeatures(),
        description = toDomain().description
    )

    fun toDetails() = GithubRepositoryDetails(
        repository = toDomain(),
        ownerLogin = owner?.login ?: fullName.substringBefore('/'),
        ownerAvatarUrl = owner?.avatarUrl,
        defaultBranch = defaultBranch,
        forks = forks,
        watchers = watchers,
        openIssues = openIssues,
        license = license?.spdxId?.takeUnless { it == "NOASSERTION" } ?: license?.name,
        topics = topics,
        isArchived = archived,
        isFork = fork,
        viewerRole = permissions?.toRole() ?: GithubCollaboratorRole.UNKNOWN,
        features = toFeatures()
    )
}

@Serializable
data class GithubRepositoryPermissionsDto(
    val pull: Boolean = false,
    val triage: Boolean = false,
    val push: Boolean = false,
    val maintain: Boolean = false,
    val admin: Boolean = false
) {
    fun toRole(): GithubCollaboratorRole = when {
        admin -> GithubCollaboratorRole.ADMIN
        maintain -> GithubCollaboratorRole.MAINTAIN
        push -> GithubCollaboratorRole.WRITE
        triage -> GithubCollaboratorRole.TRIAGE
        pull -> GithubCollaboratorRole.READ
        else -> GithubCollaboratorRole.UNKNOWN
    }
}

@Serializable
data class GithubRepositoryOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GithubRepositoryLicenseDto(
    val name: String? = null,
    @SerialName("spdx_id") val spdxId: String? = null
)
