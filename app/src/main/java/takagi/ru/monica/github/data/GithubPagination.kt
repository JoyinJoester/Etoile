package takagi.ru.monica.github.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object GithubPagination {
    fun nextPage(linkHeader: String?): Int? {
        return nextLink(linkHeader)?.queryParameter("page")?.toIntOrNull()
    }

    fun nextCursor(linkHeader: String?): String? =
        nextLink(linkHeader)?.queryParameter("cursor")?.takeIf(String::isNotBlank)

    private fun nextLink(linkHeader: String?): okhttp3.HttpUrl? {
        val nextLink = linkHeader
            ?.split(',')
            ?.firstOrNull { it.substringAfter(';', missingDelimiterValue = "").contains("rel=\"next\"") }
            ?.substringBefore(';')
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")
            ?: return null
        return nextLink.toHttpUrlOrNull()
    }
}
