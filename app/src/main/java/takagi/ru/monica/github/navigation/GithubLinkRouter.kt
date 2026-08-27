package takagi.ru.monica.github.navigation

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Destinations that can be opened inside the native GitHub experience. */
sealed interface GithubLinkDestination {
    data class Repository(val fullName: String) : GithubLinkDestination
    data class Issue(val fullName: String, val number: Int) : GithubLinkDestination
    data class PullRequest(val fullName: String, val number: Int) : GithubLinkDestination
    data class ActionsRun(val fullName: String, val runId: Long) : GithubLinkDestination
    data class ActionsJob(val fullName: String, val jobId: Long) : GithubLinkDestination
    data class Releases(val fullName: String) : GithubLinkDestination
    data class ReleaseTag(val fullName: String, val tagName: String) : GithubLinkDestination
    data class Commit(val fullName: String, val sha: String) : GithubLinkDestination
    data class User(val login: String) : GithubLinkDestination
    data class UserFollowers(val login: String) : GithubLinkDestination
    data class UserFollowing(val login: String) : GithubLinkDestination
}

/**
 * Converts supported github.com URLs into type-safe app destinations.
 * Unknown GitHub pages deliberately return null so callers can open them
 * externally instead of guessing a route and losing user context.
 */
object GithubLinkRouter {
    fun parse(url: String?): GithubLinkDestination? {
        val parsed = url?.trim()?.toHttpUrlOrNull() ?: return null
        if (parsed.host !in setOf("github.com", "www.github.com")) return null

        val segments = parsed.pathSegments.filter(String::isNotBlank)
        if (segments.size == 1) {
            val login = segments.first()
            if (!isValidUserLogin(login)) return null
            return when (parsed.queryParameter("tab")?.lowercase()) {
                "followers" -> GithubLinkDestination.UserFollowers(login)
                "following" -> GithubLinkDestination.UserFollowing(login)
                else -> GithubLinkDestination.User(login)
            }
        }
        if (segments.size < 2) return null
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (owner.isBlank() || repository.isBlank()) return null
        val fullName = "$owner/$repository"

        if (segments.size == 2) return GithubLinkDestination.Repository(fullName)

        val kind = segments[2].lowercase()
        val number = segments.getOrNull(3)?.toLongOrNull()
        val actionKind = segments.getOrNull(3)?.lowercase()
        val actionId = segments.getOrNull(4)?.toLongOrNull()
        val releaseTag = segments.drop(4).joinToString("/").takeIf { it.isValidReleaseTag() }
        val commitSha = segments.getOrNull(3)?.takeIf { it.isValidCommitSha() }
        return when {
            kind == "issues" && number.isValidIssueNumber() ->
                GithubLinkDestination.Issue(fullName, number!!.toInt())
            kind == "pull" && number.isValidIssueNumber() ->
                GithubLinkDestination.PullRequest(fullName, number!!.toInt())
            kind == "actions" && actionKind == "runs" && actionId.isValidId() ->
                GithubLinkDestination.ActionsRun(fullName, actionId!!)
            kind == "actions" && actionKind == "jobs" && actionId.isValidId() ->
                GithubLinkDestination.ActionsJob(fullName, actionId!!)
            kind == "releases" && segments.size == 3 ->
                GithubLinkDestination.Releases(fullName)
            kind == "releases" && actionKind == "latest" ->
                GithubLinkDestination.Releases(fullName)
            kind == "releases" && actionKind == "tag" && releaseTag != null ->
                GithubLinkDestination.ReleaseTag(fullName, releaseTag)
            kind == "commit" && commitSha != null ->
                GithubLinkDestination.Commit(fullName, commitSha)
            else -> null
        }
    }

    private fun Long?.isValidIssueNumber(): Boolean = this != null && this in 1..Int.MAX_VALUE

    private fun Long?.isValidId(): Boolean = this != null && this > 0

    private fun String.isValidReleaseTag(): Boolean =
        isNotBlank() && length <= 255 && none(Char::isISOControl)

    private fun String.isValidCommitSha(): Boolean =
        length in 7..255 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    private fun isValidUserLogin(value: String): Boolean =
        value.lowercase() !in RESERVED_USER_PATHS &&
            value.length in 1..39 && value.first().isLetterOrDigit() &&
            value.last().isLetterOrDigit() && value.all { it.isLetterOrDigit() || it == '-' }

    private val RESERVED_USER_PATHS = setOf(
        "about", "collections", "codespaces", "contact", "customer-stories", "explore",
        "features", "login", "marketplace", "new", "notifications", "orgs", "organizations",
        "pricing", "pulls", "security", "settings", "signup", "sponsors", "topics"
    )
}
