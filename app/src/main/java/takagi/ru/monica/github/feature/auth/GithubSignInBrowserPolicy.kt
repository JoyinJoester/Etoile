package takagi.ru.monica.github.feature.auth

import java.net.URI

internal object GithubSignInBrowserPolicy {
    fun isAuthorizationPage(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port in listOf(-1, 443) && uri.rawFragment == null &&
            uri.rawPath in listOf("/login/device", "/login/device/", "/login/oauth/authorize")
    }.getOrDefault(false)
}
