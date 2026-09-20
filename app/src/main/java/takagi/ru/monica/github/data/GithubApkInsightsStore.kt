package takagi.ru.monica.github.data

import android.content.Context

/** 记录商店条目解析出的真实包名，用于"已安装/可更新"徽标的版本比对。 */
class GithubApkInsightsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun remember(repoFullName: String, packageName: String, versionName: String?) {
        prefs.edit()
            .putString(key(repoFullName), packageName)
            .putString(versionKey(repoFullName), versionName)
            .apply()
    }

    fun packageName(repoFullName: String): String? = prefs.getString(key(repoFullName), null)

    private fun key(repoFullName: String) = "pkg:${repoFullName.lowercase()}"

    private fun versionKey(repoFullName: String) = "ver:${repoFullName.lowercase()}"

    private companion object {
        private const val PREFS_NAME = "github_apk_insights"
    }
}
