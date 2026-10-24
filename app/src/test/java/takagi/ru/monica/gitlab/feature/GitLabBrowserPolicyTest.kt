package takagi.ru.monica.gitlab.feature

import org.junit.Assert.*
import org.junit.Test

class GitLabBrowserPolicyTest {
    @Test fun permitsOnlyTheSelectedInstanceAndItsAuthorizationEndpoint() {
        assertTrue(GitLabBrowserPolicy.allows("https://gitlab.com/oauth/authorize?client_id=public", "https://gitlab.com/"))
        assertTrue(GitLabBrowserPolicy.allows("https://gitlab.com/", "https://gitlab.com"))
        assertTrue(GitLabBrowserPolicy.allows("https://git.example/gitlab/oauth/authorize?state=x", "https://git.example/gitlab/"))
        assertFalse(GitLabBrowserPolicy.allows("https://gitlab.com.attacker.example/oauth/authorize", "https://gitlab.com/"))
        assertFalse(GitLabBrowserPolicy.allows("https://github.com/login/oauth/authorize", "https://gitlab.com/"))
        assertFalse(GitLabBrowserPolicy.allows("https://git.example/oauth/authorize", "https://git.example/gitlab/"))
        assertFalse(GitLabBrowserPolicy.allows("https://gitlab.com:8443/oauth/authorize", "https://gitlab.com/"))
    }

    @Test fun refusesCleartextCredentialsFragmentsAndUnrelatedPages() {
        for (url in listOf("http://gitlab.com/oauth/authorize", "https://user:secret@gitlab.com/oauth/authorize",
            "https://gitlab.com/oauth/authorize#fragment", "https://gitlab.com/redirect?url=elsewhere")) {
            assertFalse(url, GitLabBrowserPolicy.allows(url, "https://gitlab.com/"))
        }
    }
}
