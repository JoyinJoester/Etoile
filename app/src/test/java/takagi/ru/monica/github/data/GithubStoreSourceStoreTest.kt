package takagi.ru.monica.github.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GithubStoreSourceStoreTest {
    @Test fun sourcesAreScopedToAccountAndAnonymousSourcesRemainSeparate() {
        val context = RuntimeEnvironment.getApplication()
        val root = GithubStoreSourceStore(context)
        val first = root.forAccount(1)
        val second = root.forAccount(2)
        val anonymous = root.forAccount(null)
        assertTrue(first.add("private/app"))
        assertTrue(anonymous.add("public/app"))
        assertTrue(second.sources().isEmpty())
        assertEquals(listOf("private/app"), first.sources())
        val restored = GithubStoreSourceStore(context)
        assertEquals(listOf("private/app"), restored.forAccount(1).sources())
        assertEquals(listOf("public/app"), restored.forAccount(null).sources())
        assertTrue(restored.forAccount(2).sources().isEmpty())
    }

    @Test fun legacyPrivateSourcesAreClaimedOnceAndNeverExposedWhileSignedOut() {
        val context = RuntimeEnvironment.getApplication()
        val root = GithubStoreSourceStore(context)
        root.add("old/private-app")
        assertTrue(root.forAccount(null).sources().isEmpty())
        assertEquals(listOf("old/private-app"), root.forAccount(10).sources())
        assertTrue(root.forAccount(20).sources().isEmpty())
        val restored = GithubStoreSourceStore(context)
        assertEquals(listOf("old/private-app"), restored.forAccount(10).sources())
        assertTrue(restored.forAccount(30).sources().isEmpty())
    }
}
