package takagi.ru.monica.ui.components

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class MarkdownImageContext(val fullName: String, val ref: String = "HEAD", val sourcePath: String = "")

/** Resolve image bytes, preserving the document's directory, ref and encoded paths. */
internal fun resolveMarkdownImage(target: String, context: MarkdownImageContext? = null, baseUrl: String? = null): String? {
    val link = target.trim().removeSurrounding("<", ">")
    val absolute = when {
        link.startsWith("//") -> "https:$link".toHttpUrlOrNull()
        Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(link) -> link.toHttpUrlOrNull()
        baseUrl != null && link.isNotEmpty() && !link.startsWith('#') -> baseUrl.toHttpUrlOrNull()?.resolve(link)
        context != null && link.isNotEmpty() && !link.startsWith('#') -> {
            // Resolve ../ inside a virtual repository root before adding owner/ref.
            val directory = "https://raw.githubusercontent.com/".toHttpUrl().newBuilder()
                .addPathSegments(context.sourcePath.substringBeforeLast('/', ""))
                .addPathSegment("").build()
            val relative = directory.resolve(link) ?: return null
            "https://raw.githubusercontent.com/".toHttpUrl().newBuilder()
                .addPathSegments(context.fullName).addPathSegment(context.ref)
                .addEncodedPathSegments(relative.encodedPath.removePrefix("/"))
                .encodedQuery(relative.encodedQuery).build()
        }
        else -> null
    } ?: return null
    if (absolute.username.isNotEmpty() || absolute.password.isNotEmpty()) return null
    val segments = absolute.encodedPathSegments
    return if (absolute.host == "github.com" && segments.size >= 5 && segments[2] in setOf("blob", "raw")) {
        "https://raw.githubusercontent.com/".toHttpUrl().newBuilder()
            .addEncodedPathSegments((segments.take(2) + segments.drop(3)).joinToString("/"))
            .encodedQuery(absolute.encodedQuery).build().toString()
    } else absolute.newBuilder().fragment(null).build().toString()
}

/** Standard document links for providers with a canonical web URL. No credentials are forwarded. */
internal fun resolveMarkdownDocumentLink(target: String, baseUrl: String?): String? {
    val link = target.trim().removeSurrounding("<", ">")
    if (link.startsWith("mailto:", ignoreCase = true)) return link
    val absolute = link.toHttpUrlOrNull() ?: baseUrl?.toHttpUrlOrNull()?.resolve(link) ?: return null
    if (absolute.username.isNotEmpty() || absolute.password.isNotEmpty()) return null
    return absolute.toString()
}

/** Compatibility entry point for callers preparing HTML image fragments. */
internal fun prepareMarkdownImages(markdown: String): String = prepareMarkdownContent(markdown)
