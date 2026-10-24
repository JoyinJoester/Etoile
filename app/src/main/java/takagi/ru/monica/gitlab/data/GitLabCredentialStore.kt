package takagi.ru.monica.gitlab.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import takagi.ru.monica.gitlab.domain.GitLabAccount

@Serializable
data class GitLabCredential(
    val id: Long,
    val instanceUrl: String,
    val username: String,
    val name: String,
    val webUrl: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val clientId: String? = null
) {
    fun account() = GitLabAccount(id, instanceUrl, username, name, webUrl)
    override fun toString() = "GitLabCredential(account=${account().key}, tokens=<redacted>)"
}

@Serializable
data class GitLabPendingAuthorization(
    val instanceUrl: String,
    val clientId: String,
    val state: String,
    val verifier: String,
    val createdAt: Long
) {
    override fun toString() = "GitLabPendingAuthorization(<redacted>)"
}

@Serializable
data class GitLabStoredAccounts(
    val credentials: List<GitLabCredential> = emptyList(),
    val activeKey: String? = null
) {
    override fun toString() = "GitLabStoredAccounts(count=${credentials.size})"
}

interface GitLabCredentialStore {
    fun read(): GitLabStoredAccounts
    fun write(accounts: GitLabStoredAccounts)
    fun pending(): GitLabPendingAuthorization?
    fun writePending(pending: GitLabPendingAuthorization?)
}

class GitLabEncryptedCredentialStore internal constructor(preferencesFactory: () -> SharedPreferences) : GitLabCredentialStore {
    constructor(context: Context) : this({ EncryptedSharedPreferences.create(
        context.applicationContext,
        "etoile_gitlab_credentials",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ) })
    // Initialization failures belong to the repository's Result boundary, not
    // application construction. A failed attempt can be retried without
    // deleting credentials or falling back to unencrypted storage.
    private val preferences by lazy(preferencesFactory)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(): GitLabStoredAccounts = preferences.getString("accounts", null)?.let {
        json.decodeFromString<GitLabStoredAccounts>(it)
    } ?: GitLabStoredAccounts()

    @Synchronized
    override fun write(accounts: GitLabStoredAccounts) {
        check(preferences.edit().putString("accounts", json.encodeToString(GitLabStoredAccounts.serializer(), accounts)).commit())
    }

    @Synchronized
    override fun pending(): GitLabPendingAuthorization? = preferences.getString("pending", null)?.let {
        json.decodeFromString<GitLabPendingAuthorization>(it)
    }

    @Synchronized
    override fun writePending(pending: GitLabPendingAuthorization?) {
        val editor = preferences.edit()
        if (pending == null) editor.remove("pending")
        else editor.putString("pending", json.encodeToString(GitLabPendingAuthorization.serializer(), pending))
        check(editor.commit())
    }
}
