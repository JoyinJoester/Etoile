package takagi.ru.monica.github.data

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.github.domain.*

internal interface GithubActionJournalStorage {
    fun entries(): Map<String, String>
    fun write(key: String, value: String?): Boolean
}

/** One application-owned instance coordinates live attempts; disk markers survive navigation and restart. */
open class GithubActionJournalImpl internal constructor(
    private val storage: GithubActionJournalStorage,
    private val dispatcher: CoroutineDispatcher
) : GithubActionJournal {
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val active = mutableMapOf<String, String>()

    override fun observe(scope: GithubActionScope): Flow<Set<GithubActionOperation>> = revision.map {
        withContext(dispatcher) {
            mutex.withLock {
                val prefix = prefix(scope)
                storage.entries().keys.filter { it.startsWith(prefix) }.mapNotNull { key ->
                    val parts = key.removePrefix(prefix).split(':')
                    val kind = parts.getOrNull(0)?.let { value -> GithubActionKind.entries.find { it.name == value } }
                    val id = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
                    if (parts.size == 2 && kind != null && id != null) GithubActionOperation(kind, id) else null
                }.toSet()
            }
        }
    }.distinctUntilChanged()

    override suspend fun begin(scope: GithubActionScope, operation: GithubActionOperation, repeatConfirmed: Boolean): GithubActionAttempt =
        withContext(dispatcher) {
            mutex.withLock {
                require(operation.targetId > 0)
                val key = key(scope, operation)
                check(key !in active) { "Action already in progress" }
                check(repeatConfirmed || key !in storage.entries()) { "Check the previous action before repeating it" }
                val attempt = GithubActionAttempt(scope, operation, UUID.randomUUID().toString())
                if (!storage.write(key, attempt.id)) {
                    revision.value++
                    throw IOException("Action status could not be saved")
                }
                active[key] = attempt.id
                revision.value++
                attempt
            }
        }

    override suspend fun finish(attempt: GithubActionAttempt, acceptedSuccess: Boolean) {
        withContext(NonCancellable + dispatcher) {
            mutex.withLock {
                val key = key(attempt.scope, attempt.operation)
                try {
                    if (acceptedSuccess && storage.entries()[key] == attempt.id && !storage.write(key, null)) {
                        throw IOException("Action status could not be cleared")
                    }
                } finally {
                    if (active[key] == attempt.id) active.remove(key)
                    revision.value++
                }
            }
        }
    }

    override suspend fun acknowledge(scope: GithubActionScope, operation: GithubActionOperation) = withContext(dispatcher) {
        mutex.withLock {
            val key = key(scope, operation)
            check(key !in active) { "Action still in progress" }
            if (!storage.write(key, null)) throw IOException("Action status could not be cleared")
            revision.value++
            Unit
        }
    }

    // Hash the account/repository scope; preference values contain only random attempt identifiers.
    private fun prefix(scope: GithubActionScope) = sha256Hex("${scope.accountId}\u0000${scope.owner.lowercase(java.util.Locale.ROOT)}\u0000${scope.name.lowercase(java.util.Locale.ROOT)}") + ":"
    private fun key(scope: GithubActionScope, operation: GithubActionOperation) = prefix(scope) + "${operation.kind}:${operation.targetId}"
}

class GithubPreferencesActionJournal(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    preferencesFactory: (() -> SharedPreferences)? = null
) : GithubActionJournalImpl(object : GithubActionJournalStorage {
    private val preferences by lazy {
        preferencesFactory?.invoke() ?: context.applicationContext.getSharedPreferences("github_action_outcomes", Context.MODE_PRIVATE)
    }
    override fun entries(): Map<String, String> = preferences.all.mapValues { (_, value) ->
        value as? String ?: throw IOException("Action status is unreadable")
    }
    override fun write(key: String, value: String?): Boolean {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        return editor.commit()
    }
}, dispatcher)

/** Preview and unit-test hosts use memory; production injects the persistent application instance. */
class InMemoryGithubActionJournal : GithubActionJournalImpl(object : GithubActionJournalStorage {
    private val entries = mutableMapOf<String, String>()
    override fun entries(): Map<String, String> = entries.toMap()
    override fun write(key: String, value: String?): Boolean {
        if (value == null) entries.remove(key) else entries[key] = value
        return true
    }
}, Dispatchers.Unconfined)
