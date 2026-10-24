package takagi.ru.monica.gitlab.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object GitLabBrowserPolicy {
    fun allows(url: String, instance: String): Boolean {
        val base = instance.trim().toHttpUrlOrNull() ?: return false
        val target = url.toHttpUrlOrNull() ?: return false
        if (!base.isHttps || !target.isHttps || base.host != target.host || base.port != target.port ||
            base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null ||
            target.username.isNotEmpty() || target.password.isNotEmpty() || target.fragment != null) return false
        val root = base.encodedPath.let { if (it.endsWith('/')) it else "$it/" }
        return (target.encodedPath == root && target.query == null) ||
            target.encodedPath == root + "oauth/authorize"
    }
}

/** GitLab has a separate browser policy so GitHub's host allowlist stays intact. */
internal object GitLabSignInBrowser {
    fun open(context: Context, url: String, instances: List<String>): Boolean {
        if (instances.none { GitLabBrowserPolicy.allows(url, it) }) return false
        val uri = Uri.parse(url)
        val opened = runCatching {
            val provider = CustomTabsClient.getPackageName(context, null) ?: return@runCatching false
            CustomTabsIntent.Builder().setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF).build().apply {
                    intent.setPackage(provider)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.launchUrl(context, uri)
            true
        }.getOrDefault(false)
        if (opened) return true
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
