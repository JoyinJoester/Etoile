package takagi.ru.monica.github.data

import android.content.Context
import android.content.SharedPreferences

/** 商店自定义来源的本地持久化(owner/name 集合)。 */
class GithubStoreSourceStore private constructor(private val prefs: SharedPreferences, private val sourcesKey: String) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KEY_SOURCES)

    fun forAccount(accountId: Long?): GithubStoreSourceStore = synchronized(prefs) {
        val key = if (accountId == null) "public_sources" else "account:$accountId:sources"
        if (accountId != null && !prefs.contains("legacy_sources_owner")) {
            val legacy = prefs.getStringSet(KEY_SOURCES, emptySet()).orEmpty()
            // Old versions stored no owner. Claim once; never copy private names
            // into every account or expose them while signed out.
            check(prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() + legacy)
                .putLong("legacy_sources_owner", accountId).remove(KEY_SOURCES).commit())
        }
        GithubStoreSourceStore(prefs, key)
    }

    fun sources(): List<String> =
        prefs.getStringSet(sourcesKey, emptySet())?.sorted().orEmpty()

    fun add(fullName: String): Boolean = synchronized(prefs) {
        val updated = sources() + fullName
        prefs.edit().putStringSet(sourcesKey, updated.toSet()).commit()
    }

    fun remove(fullName: String): Boolean = synchronized(prefs) {
        val updated = sources() - fullName
        prefs.edit().putStringSet(sourcesKey, updated.toSet()).commit()
    }

    fun enabledFdroidSources(): Set<String> = prefs.getStringSet("fdroid_enabled", null)?.toSet()
        ?: takagi.ru.monica.github.domain.FdroidSources.defaults
    fun setFdroidEnabled(url: String, enabled: Boolean) {
        val current = enabledFdroidSources()
        prefs.edit().putStringSet("fdroid_enabled", if (enabled) current + url else current - url).apply()
    }
    private companion object {
        private const val PREFS_NAME = "github_store_sources"
        private const val KEY_SOURCES = "sources"
    }
}
