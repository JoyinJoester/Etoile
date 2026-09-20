package takagi.ru.monica.github.feature.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** Uses an installed browser, never a bundled rendering engine. */
internal object GithubSignInBrowser {
    fun open(context: Context, url: String): Boolean {
        if (!GithubSignInBrowserPolicy.isAuthorizationPage(url)) return false
        val uri = Uri.parse(url)
        val opened = runCatching {
            val provider = CustomTabsClient.getPackageName(context, null)
            if (provider == null) return@runCatching false
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build().apply {
                    intent.setPackage(provider)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.launchUrl(context, uri)
            true
        }.getOrDefault(false)
        if (opened) return true
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
