package takagi.ru.monica.github.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubSignInBrowserPolicyTest {
    @Test fun opensOnlyGithubAuthorizationPages() {
        listOf("https://github.com/login/device", "https://github.com/login/device/",
            "https://github.com/login/oauth/authorize?state=test&client_id=example").forEach {
            assertTrue(it, GithubSignInBrowserPolicy.isAuthorizationPage(it))
        }
    }

    @Test fun rejectsOtherOriginsSchemesAndPaths() {
        listOf("http://github.com/login/device", "https://github.com.evil.test/login/device",
            "https://github.com@evil.test/login/device", "https://evil@github.com/login/device",
            "https://github.com:444/login/device", "https://github.com/login/device#other",
            "https://github.com/settings", "https://github.com/login/device/other", "javascript:alert(1)",
            "intent://login/device", "etoile://oauth", "not a URL").forEach {
            assertFalse(it, GithubSignInBrowserPolicy.isAuthorizationPage(it))
        }
    }
}
