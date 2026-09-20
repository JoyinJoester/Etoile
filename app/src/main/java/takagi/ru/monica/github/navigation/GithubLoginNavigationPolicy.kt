package takagi.ru.monica.github.navigation

/** How the in-app sign-in WebView must treat a navigation request. */
enum class GithubLoginNavigation { CALLBACK, IN_WEBVIEW, EXTERNAL, BLOCK }

/**
 * Keeps the sign-in container on GitHub's own sign-in pages. The OAuth callback
 * goes back to the app, every other HTTPS page (help links, enterprise SSO)
 * leaves for the system browser, and anything that is not plain HTTPS is
 * refused so a redirect cannot steer the container towards `intent://`,
 * `file://` or `javascript:` targets.
 */
object GithubLoginNavigationPolicy {
    fun classify(url: String): GithubLoginNavigation {
        val trimmed = url.trim()
        if (trimmed.equals(BLANK_PAGE, ignoreCase = true)) return GithubLoginNavigation.IN_WEBVIEW
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return GithubLoginNavigation.BLOCK
        val scheme = trimmed.take(schemeEnd).lowercase()
        val host = hostOf(trimmed.substring(schemeEnd + "://".length)) ?: return GithubLoginNavigation.BLOCK
        if (scheme == CALLBACK_SCHEME) {
            return if (host == CALLBACK_HOST) GithubLoginNavigation.CALLBACK else GithubLoginNavigation.BLOCK
        }
        if (scheme != "https") return GithubLoginNavigation.BLOCK
        return if (host == GITHUB_HOST || host == "www.$GITHUB_HOST") {
            GithubLoginNavigation.IN_WEBVIEW
        } else {
            GithubLoginNavigation.EXTERNAL
        }
    }

    private fun hostOf(afterScheme: String): String? {
        val authority = afterScheme.takeWhile { it != '/' && it != '\\' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.takeIf(HOST_PATTERN::matches)
    }

    private const val BLANK_PAGE = "about:blank"
    private const val CALLBACK_SCHEME = "etoile"
    private const val CALLBACK_HOST = "oauth"
    private const val GITHUB_HOST = "github.com"
    private val HOST_PATTERN = Regex("""[a-z0-9]([a-z0-9.-]*[a-z0-9])?""")
}
