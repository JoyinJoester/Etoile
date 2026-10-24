package takagi.ru.monica.github.domain

import kotlinx.coroutines.flow.Flow

data class GithubActionScope(val accountId: Long, val owner: String, val name: String)
enum class GithubActionKind { RUN, TOGGLE, DISPATCH, RELEASE_CREATE, RELEASE_CHANGE, RELEASE_UPLOAD, RELEASE_ASSET_DELETE }
data class GithubActionOperation(val kind: GithubActionKind, val targetId: Long)
data class GithubActionAttempt(val scope: GithubActionScope, val operation: GithubActionOperation, val id: String)

/** Durable uncertainty, never a queue of operations to replay. Contains no ref, inputs or credentials. */
interface GithubActionJournal {
    fun observe(scope: GithubActionScope): Flow<Set<GithubActionOperation>>
    /** Must persist before sending. Reject a pending outcome without explicit consent, or any live attempt. */
    suspend fun begin(scope: GithubActionScope, operation: GithubActionOperation, repeatConfirmed: Boolean): GithubActionAttempt
    /** A cancellation or failure retains the marker; accepted success clears only this attempt. */
    suspend fun finish(attempt: GithubActionAttempt, acceptedSuccess: Boolean)
    /** Local acknowledgement after checking the server. Must not clear an in-flight attempt. */
    suspend fun acknowledge(scope: GithubActionScope, operation: GithubActionOperation)
}
