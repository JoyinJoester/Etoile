package takagi.ru.monica.github.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubAvatarTest {
    @Test
    fun avatarUrlsAcceptOnlyTrimmedHttpsWebUrls() {
        assertEquals(
            "https://avatars.githubusercontent.com/u/7?v=4",
            normalizeGithubAvatarUrl("  https://avatars.githubusercontent.com/u/7?v=4  ")
        )
        assertNull(normalizeGithubAvatarUrl("http://avatars.example/user.png"))
        assertNull(normalizeGithubAvatarUrl("file:///data/user/0/avatar.png"))
        assertNull(normalizeGithubAvatarUrl("javascript:alert(1)"))
        assertNull(normalizeGithubAvatarUrl("  "))
        assertNull(normalizeGithubAvatarUrl(null))
    }
}
