package takagi.ru.monica.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.github.domain.GithubStoreSource

class GithubStoreSourceTest {

    @Test
    fun `parses plain owner and name`() {
        assertEquals("owner/name", GithubStoreSource.normalize("owner/name"))
    }

    @Test
    fun `parses full https url`() {
        assertEquals("owner/name", GithubStoreSource.normalize("https://github.com/owner/name"))
    }

    @Test
    fun `parses url with trailing slash and git suffix`() {
        assertEquals("owner/name", GithubStoreSource.normalize("https://github.com/owner/name.git/"))
    }

    @Test
    fun `parses deep url down to repo`() {
        assertEquals("owner/name", GithubStoreSource.normalize("https://github.com/owner/name/releases"))
    }

    @Test
    fun `rejects non repository paths`() {
        assertNull(GithubStoreSource.normalize("https://github.com/topics/android"))
        assertNull(GithubStoreSource.normalize("https://github.com/search?q=apk"))
        assertNull(GithubStoreSource.normalize("justaname"))
        assertNull(GithubStoreSource.normalize(""))
    }

    @Test
    fun `trims whitespace`() {
        assertEquals("owner/name", GithubStoreSource.normalize("  owner/name  "))
    }
}
