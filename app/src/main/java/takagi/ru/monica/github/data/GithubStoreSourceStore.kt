package takagi.ru.monica.github.data

import android.content.Context

/** 商店自定义来源的本地持久化(owner/name 集合)。 */
class GithubStoreSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sources(): List<String> =
        prefs.getStringSet(KEY_SOURCES, emptySet())?.sorted().orEmpty()

    fun add(fullName: String): Boolean {
        val updated = sources() + fullName
        return prefs.edit().putStringSet(KEY_SOURCES, updated.toSet()).commit()
    }

    fun remove(fullName: String): Boolean {
        val updated = sources() - fullName
        return prefs.edit().putStringSet(KEY_SOURCES, updated.toSet()).commit()
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
