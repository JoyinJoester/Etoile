package takagi.ru.monica.github.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubLoginNavigationPolicyTest {
    @Test
    fun oauthCallbackReturnsToTheApp() {
        assertEquals(
            GithubLoginNavigation.CALLBACK,
            GithubLoginNavigationPolicy.classify("etoile://oauth?code=temporary-code&state=opaque")
        )
        assertEquals(
            GithubLoginNavigation.CALLBACK,
            GithubLoginNavigationPolicy.classify("ETOILE://OAuth?error=access_denied")
        )
    }

    @Test
    fun githubPagesStayInsideTheSignInContainer() {
        listOf(
            "https://github.com/login/oauth/authorize?client_id=1",
            "https://github.com/sessions/two-factor",
            "https://www.github.com/login",
            "about:blank"
        ).forEach { url ->
            assertEquals(url, GithubLoginNavigation.IN_WEBVIEW, GithubLoginNavigationPolicy.classify(url))
        }
    }

    @Test
    fun otherHttpsDestinationsLeaveForTheSystemBrowser() {
        listOf(
            "https://docs.github.com/en/apps",
            "https://gist.github.com/octocat",
            "https://accounts.google.com/o/oauth2/auth"
        ).forEach { url ->
            assertEquals(url, GithubLoginNavigation.EXTERNAL, GithubLoginNavigationPolicy.classify(url))
        }
    }

    @Test
    fun lookalikeHostsDoNotCountAsGithub() {
        listOf(
            "https://github.com.evil.example/login",
            "https://notgithub.com/login",
            "https://evil.example/?next=github.com"
        ).forEach { url ->
            assertEquals(url, GithubLoginNavigation.EXTERNAL, GithubLoginNavigationPolicy.classify(url))
        }
    }

    @Test
    fun credentialsInTheAuthorityCannotForgeTheGithubHost() {
        assertEquals(
            GithubLoginNavigation.EXTERNAL,
            GithubLoginNavigationPolicy.classify("https://github.com@evil.example/login")
        )
        assertEquals(
            GithubLoginNavigation.BLOCK,
            GithubLoginNavigationPolicy.classify("etoile://oauth@evil.example/callback")
        )
    }

    @Test
    fun nonHttpsAndMalformedTargetsAreRefused() {
        listOf(
            "http://github.com/login",
            "javascript:alert(1)",
            "file:///data/data/app.etoile/databases",
            "intent://github.com/login#Intent;scheme=https;end",
            "content://media/external/images/1",
            "data:text/html,<script>fetch('https://evil.example')</script>",
            "github.com/login",
            ""
        ).forEach { url ->
            assertEquals(url, GithubLoginNavigation.BLOCK, GithubLoginNavigationPolicy.classify(url))
        }
    }
}
