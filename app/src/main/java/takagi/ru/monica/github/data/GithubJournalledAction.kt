package takagi.ru.monica.github.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import takagi.ru.monica.github.data.githubRunCatching
import takagi.ru.monica.github.domain.*

/** Persists before IO; a late/cancelled success is not an accepted acknowledgement of the original action. */
internal suspend fun <T> GithubActionJournal.runAction(
    scope: GithubActionScope,
    operation: GithubActionOperation,
    repeatConfirmed: Boolean,
    stillCurrent: () -> Boolean,
    block: suspend () -> Result<T>
): Result<T> = githubRunCatching {
    currentCoroutineContext().ensureActive()
    if (!stillCurrent()) throw CancellationException("Account changed")
    var attempt: GithubActionAttempt? = null
    var acceptedSuccess = false
    try {
        // Finish the short persistence transaction even if the caller cancels; keep its attempt handle
        // so finally can always release the in-memory flight without losing the durable warning.
        attempt = withContext(NonCancellable) { begin(scope, operation, repeatConfirmed) }
        currentCoroutineContext().ensureActive()
        if (!stillCurrent()) throw CancellationException("Account changed")
        val result = block()
        currentCoroutineContext().ensureActive()
        if (!stillCurrent()) throw CancellationException("Account changed")
        acceptedSuccess = result.isSuccess
        result.getOrThrow()
    } finally {
        attempt?.let { finish(it, acceptedSuccess) }
    }
}
