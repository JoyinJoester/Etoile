package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubUserSummary

@Serializable
internal data class GithubUserDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String
) {
    fun toDomain() = GithubUserSummary(login = login, avatarUrl = avatarUrl, htmlUrl = htmlUrl)
}

@Serializable
internal data class GithubLabelDto(
    val name: String,
    val color: String = "",
    val description: String? = null
) {
    fun toDomain() = GithubIssueLabel(name = name, color = color, description = description)
}

internal fun GithubUserDto?.toDomainOrGhost(): GithubUserSummary =
    this?.toDomain() ?: GithubUserSummary(
        login = "ghost",
        avatarUrl = null,
        htmlUrl = "https://github.com/ghost"
    )
