package takagi.ru.monica.github.navigation

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object GithubWebUrls {
    private val baseUrl: HttpUrl = "https://github.com/".toHttpUrl()

    fun repository(fullName: String): String = build(fullName)

    fun userRepositories(login: String): String = baseUrl.newBuilder()
        .addPathSegment(login)
        .addQueryParameter("tab", "repositories")
        .build()
        .toString()

    fun user(login: String): String = baseUrl.newBuilder()
        .addPathSegment(login)
        .build()
        .toString()

    fun userFollowers(login: String): String = userTab(login, "followers")

    fun userFollowing(login: String): String = userTab(login, "following")

    fun discussions(): String = baseUrl.newBuilder().addPathSegment("discussions").build().toString()

    fun userProjects(login: String): String = baseUrl.newBuilder()
        .addPathSegment("users")
        .addPathSegment(login)
        .addPathSegment("projects")
        .build()
        .toString()

    /** Normalizes a profile-provided website and rejects non-web schemes. */
    fun external(value: String): String? {
        val normalized = value.trim().let { raw ->
            if (raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
            ) {
                raw
            } else {
                "https://$raw"
            }
        }
        val parsed = normalized.toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank() || parsed.scheme !in setOf("http", "https")) return null
        val result = parsed.toString()
        return if (parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null) {
            result.removeSuffix("/")
        } else {
            result
        }
    }

    fun tree(fullName: String, ref: String, path: String = ""): String =
        build(fullName, "tree", ref, path)

    fun blob(fullName: String, ref: String, path: String): String =
        build(fullName, "blob", ref, path)

    fun issues(fullName: String): String = build(fullName, "issues")

    fun issue(fullName: String, number: Int): String = build(fullName, "issues", number.toString())

    fun newIssue(fullName: String, templateFileName: String? = null): String =
        build(fullName, "issues", "new").toHttpUrl().newBuilder().apply {
            templateFileName?.let { addQueryParameter("template", it) }
        }.build().toString()

    fun issueTemplateChooser(fullName: String): String = build(fullName, "issues", "new", "choose")

    fun pullRequests(fullName: String): String = build(fullName, "pulls")

    fun pullRequest(fullName: String, number: Int): String = build(fullName, "pull", number.toString())

    fun actions(fullName: String): String = build(fullName, "actions")

    fun repositorySettings(fullName: String): String = build(fullName, "settings")
    fun repositoryBranchesSettings(fullName: String): String = build(fullName, "settings", "branches")
    fun repositoryActionsSettings(fullName: String): String = build(fullName, "settings", "actions")
    fun repositoryCollaboratorsSettings(fullName: String): String = build(fullName, "settings", "access")
    fun repositoryWebhooksSettings(fullName: String): String = build(fullName, "settings", "hooks")

    fun repositoryWebhookSettings(fullName: String, webhookId: Long): String =
        build(fullName, "settings", "hooks", webhookId.toString())

    fun releases(fullName: String): String = build(fullName, "releases")

    fun tags(fullName: String): String = build(fullName, "tags")

    fun compare(fullName: String, base: String, head: String): String {
        val builder = baseUrl.newBuilder()
        fullName.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        builder.addPathSegment("compare")
        // Branch names may contain '/', so the range goes in as several segments and only
        // the dots keep the comparison meaning.
        return builder.addPathSegments("$base...$head").build().toString()
    }

    fun commits(fullName: String, ref: String): String = build(fullName, "commits", ref)

    fun actionsRun(fullName: String, runId: Long): String =
        build(fullName, "actions", "runs", runId.toString())

    fun resolveMarkdownLink(
        fullName: String,
        ref: String,
        sourcePath: String,
        target: String
    ): String {
        val normalized = target.trim()
        // Explicit URI schemes belong to the external URI handler, not the repository tree.
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(normalized)) return normalized
        if (normalized.startsWith("//")) return "https:$normalized"
        if (normalized.startsWith('/')) {
            return ("https://github.com$normalized".toHttpUrlOrNull()?.toString()) ?: repository(fullName)
        }
        if (normalized.isEmpty() || normalized.startsWith('#') || normalized.startsWith('?')) {
            val current = if (sourcePath.isBlank()) repository(fullName) else blob(fullName, ref, sourcePath)
            return current.toHttpUrl().resolve(normalized)?.toString() ?: current
        }

        val sourceDirectory = sourcePath.substringBeforeLast('/', missingDelimiterValue = "")
        // Resolve on a repository-root URL so ../ cannot consume the owner or ref.
        // HttpUrl retains existing escaping, queries, and fragments in the target.
        val directory = baseUrl.newBuilder().addPathSegments(sourceDirectory).addPathSegment("").build()
        val resolved = directory.resolve(normalized) ?: return repository(fullName)
        return blob(fullName, ref, "").toHttpUrl().newBuilder()
            .addEncodedPathSegments(resolved.encodedPath.removePrefix("/"))
            .encodedQuery(resolved.encodedQuery)
            .encodedFragment(resolved.encodedFragment)
            .build()
            .toString()
    }

    private fun build(
        fullName: String,
        mode: String? = null,
        ref: String? = null,
        path: String = ""
    ): String {
        val builder = baseUrl.newBuilder()
        fullName.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        mode?.let(builder::addPathSegment)
        ref?.let(builder::addPathSegment)
        path.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        return builder.build().toString()
    }

    private fun userTab(login: String, tab: String): String = baseUrl.newBuilder()
        .addPathSegment(login)
        .addQueryParameter("tab", tab)
        .build()
        .toString()

}
