package takagi.ru.monica.github.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object GithubPagination {
    fun nextPage(linkHeader: String?): Int? {
        val nextLink = linkHeader
            ?.split(',')
            ?.firstOrNull { it.substringAfter(';', missingDelimiterValue = "").contains("rel=\"next\"") }
            ?.substringBefore(';')
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")
            ?: return null
        return nextLink.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()
    }
}
