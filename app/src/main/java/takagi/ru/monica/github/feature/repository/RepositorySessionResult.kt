package takagi.ru.monica.github.feature.repository

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import takagi.ru.monica.github.domain.GithubSession

/** Check live auth too: its main-thread collector may not have run when a callback or queued write starts. */
internal fun Flow<GithubSession>?.matchesGithubAccount(observedAccountId: Long?, expectedAccountId: Long?): Boolean {
    if (this == null) return true // In-memory/preview hosts without a session source.
    if (expectedAccountId == null || expectedAccountId != observedAccountId) return false
    val current = (this as? StateFlow<GithubSession>)?.value ?: return true
    return (current as? GithubSession.SignedIn)?.account?.id == expectedAccountId
}

/** A repository may return Result.failure after cancellation; never apply that old result. */
internal suspend fun <T> Result<T>.currentRepositoryResult(): Result<T> {
    currentCoroutineContext().ensureActive()
    return this
}
