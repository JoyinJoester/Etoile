package takagi.ru.monica.gitlab.navigation

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class GitLabFileUrls(val document: String, val image: String)

internal fun gitLabFileUrls(instanceUrl: String, projectPath: String, sha: String, path: String): GitLabFileUrls? {
    val instance = instanceUrl.toHttpUrlOrNull() ?: return null
    if (instance.username.isNotEmpty() || instance.password.isNotEmpty() ||
        projectPath.isBlank() || sha.isBlank() || path.isBlank()) return null
    fun build(view: String) = instance.newBuilder()
        .addPathSegments(projectPath).addPathSegment("-").addPathSegment(view)
        .addPathSegment(sha).addPathSegments(path).build().toString()
    return GitLabFileUrls(build("blob"), build("raw"))
}

/** An API-provided document URL must belong to the signed-in instance, including its subpath. */
internal fun gitLabDocumentUrl(instanceUrl: String, webUrl: String): String? {
    val instance = instanceUrl.toHttpUrlOrNull() ?: return null
    val document = webUrl.toHttpUrlOrNull() ?: return null
    if (document.username.isNotEmpty() || document.password.isNotEmpty() ||
        document.scheme != instance.scheme || document.host != instance.host || document.port != instance.port) return null
    val prefix = instance.encodedPath.trimEnd('/') + "/"
    if (!document.encodedPath.startsWith(prefix)) return null
    return document.toString()
}
