package takagi.ru.monica.gitlab.feature

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import takagi.ru.monica.gitlab.domain.GitLabPage

/** Rebuild the loaded window atomically, following current server pagination. */
internal suspend fun <T> refreshGitLabPages(
    loadedPages: Int,
    load: suspend (Int) -> Result<GitLabPage<T>>
): Result<Pair<GitLabPage<T>, Int>> {
    val items = mutableListOf<T>()
    var page = 1
    var count = 0
    while (true) {
        val result = load(page)
        currentCoroutineContext().ensureActive()
        val response = result.getOrElse { return Result.failure(it) }
        items += response.items
        count++
        val next = response.nextPage
        if (next == null || count >= loadedPages.coerceAtLeast(1)) {
            return Result.success(GitLabPage(items, next) to count)
        }
        page = next
    }
}
