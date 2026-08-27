package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails

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
    val fork: Boolean = false
) {
    fun toDomain() = GithubRepository(id, name, fullName, description, language, stars, updatedAt, isPrivate, htmlUrl)

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
        isFork = fork
    )
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
