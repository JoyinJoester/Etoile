package takagi.ru.monica.github.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import takagi.ru.monica.github.domain.GithubAccount

data class GithubStoredCredential(
    val account: GithubAccount,
    val token: String
) {
    override fun toString(): String =
        "GithubStoredCredential(account=${account.login}, token=<redacted>)"
}

interface GithubTokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()

    fun storedCredentials(): List<GithubStoredCredential> = emptyList()

    fun save(account: GithubAccount, token: String) {
        write(token)
    }

    fun activate(accountId: Long): Boolean = false

    fun remove(accountId: Long): Boolean {
        clear()
        return true
    }

    fun activeAccountId(): Long? = null
}

class GithubSecureTokenStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : GithubTokenStore {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFERENCES_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): String? {
        val credentials = storedCredentials()
        val selected = activeAccountId()?.let { accountId ->
            credentials.firstOrNull { it.account.id == accountId }
        } ?: credentials.firstOrNull()
        return selected?.token ?: legacyToken()
    }

    override fun write(token: String) {
        preferences.edit()
            .putString(KEY_LEGACY_TOKEN, token)
            .remove(KEY_CREDENTIALS)
            .remove(KEY_ACTIVE_ACCOUNT_ID)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_LEGACY_TOKEN)
            .remove(KEY_CREDENTIALS)
            .remove(KEY_ACTIVE_ACCOUNT_ID)
            .apply()
    }

    override fun storedCredentials(): List<GithubStoredCredential> {
        val encoded = preferences.getString(KEY_CREDENTIALS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(StoredCredentialsPayload.serializer(), encoded)
                .accounts
                .asSequence()
                .filter { it.id > 0L && it.login.isNotBlank() && isStoredTokenValid(it.token) }
                .distinctBy(StoredCredentialDto::id)
                .take(MAX_STORED_ACCOUNTS)
                .map(StoredCredentialDto::toStoredCredential)
                .toList()
        }.getOrDefault(emptyList())
    }

    override fun save(account: GithubAccount, token: String) {
        require(account.id > 0L && account.login.isNotBlank())
        require(isStoredTokenValid(token))
        val stored = GithubStoredCredential(account, token)
        val existing = storedCredentials().toMutableList()
        val currentIndex = existing.indexOfFirst { it.account.id == account.id }
        if (currentIndex >= 0) {
            existing[currentIndex] = stored
        } else {
            existing += stored
        }
        val payload = StoredCredentialsPayload(
            accounts = existing.takeLast(MAX_STORED_ACCOUNTS).map { StoredCredentialDto.fromStoredCredential(it) }
        )
        preferences.edit()
            .putString(KEY_CREDENTIALS, json.encodeToString(StoredCredentialsPayload.serializer(), payload))
            .putLong(KEY_ACTIVE_ACCOUNT_ID, account.id)
            .remove(KEY_LEGACY_TOKEN)
            .apply()
    }

    override fun activate(accountId: Long): Boolean {
        if (storedCredentials().none { it.account.id == accountId }) return false
        preferences.edit().putLong(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
        return true
    }

    override fun remove(accountId: Long): Boolean {
        val existing = storedCredentials()
        val remaining = existing.filterNot { it.account.id == accountId }
        if (remaining.size == existing.size) return false
        val editor = preferences.edit()
        if (remaining.isEmpty()) {
            editor.remove(KEY_CREDENTIALS).remove(KEY_ACTIVE_ACCOUNT_ID)
        } else {
            val payload = StoredCredentialsPayload(
                accounts = remaining.map { StoredCredentialDto.fromStoredCredential(it) }
            )
            editor.putString(
                KEY_CREDENTIALS,
                json.encodeToString(StoredCredentialsPayload.serializer(), payload)
            )
            if (activeAccountId() == accountId || activeAccountId() == null) {
                editor.putLong(KEY_ACTIVE_ACCOUNT_ID, remaining.first().account.id)
            }
        }
        editor.apply()
        return true
    }

    override fun activeAccountId(): Long? =
        if (preferences.contains(KEY_ACTIVE_ACCOUNT_ID)) {
            preferences.getLong(KEY_ACTIVE_ACCOUNT_ID, 0L).takeIf { it > 0L }
        } else {
            null
        }

    private fun legacyToken(): String? =
        preferences.getString(KEY_LEGACY_TOKEN, null)?.takeIf(::isStoredTokenValid)

    @Serializable
    private data class StoredCredentialsPayload(
        val version: Int = STORAGE_VERSION,
        val accounts: List<StoredCredentialDto> = emptyList()
    )

    @Serializable
    private data class StoredCredentialDto(
        val id: Long,
        val login: String,
        val name: String? = null,
        val bio: String? = null,
        val avatarUrl: String,
        val htmlUrl: String,
        val publicRepositories: Int = 0,
        val followers: Int = 0,
        val following: Int = 0,
        val token: String
    ) {
        fun toStoredCredential() = GithubStoredCredential(
            account = GithubAccount(
                id = id,
                login = login,
                name = name,
                bio = bio,
                avatarUrl = avatarUrl,
                htmlUrl = htmlUrl,
                publicRepositories = publicRepositories,
                followers = followers,
                following = following
            ),
            token = token
        )

        companion object {
            fun fromStoredCredential(value: GithubStoredCredential) = StoredCredentialDto(
                id = value.account.id,
                login = value.account.login,
                name = value.account.name,
                bio = value.account.bio,
                avatarUrl = value.account.avatarUrl,
                htmlUrl = value.account.htmlUrl,
                publicRepositories = value.account.publicRepositories,
                followers = value.account.followers,
                following = value.account.following,
                token = value.token
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "etoile_github_secure_session"
        const val KEY_LEGACY_TOKEN = "access_token"
        const val KEY_CREDENTIALS = "account_credentials_v2"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id_v2"
        const val STORAGE_VERSION = 2
        const val MAX_STORED_ACCOUNTS = 20

        fun isStoredTokenValid(token: String): Boolean =
            token.length in 20..255 && token.none(Char::isWhitespace)
    }
}
