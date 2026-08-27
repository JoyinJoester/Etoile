package takagi.ru.monica.github.domain

data class GithubPage<T>(
    val items: List<T>,
    val nextPage: Int?
) {
    val hasNextPage: Boolean get() = nextPage != null
}

/**
 * Merges a page into an existing list while preserving API order and removing
 * duplicate identities. Keeping this policy in one place prevents each
 * paginated feature from subtly implementing a different append/reset rule.
 */
fun <T, K> GithubPage<T>.mergeItems(
    existing: List<T>,
    reset: Boolean,
    keySelector: (T) -> K
): List<T> = (if (reset) items else existing + items).distinctBy(keySelector)
