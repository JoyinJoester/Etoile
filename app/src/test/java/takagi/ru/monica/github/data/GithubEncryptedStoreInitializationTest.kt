package takagi.ru.monica.github.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GithubEncryptedStoreInitializationTest {
    @Test fun constructorDefersSecureStorageFailureAndEachStoreCanRetryWithoutDestructiveRecovery() {
        val context = RuntimeEnvironment.getApplication()
        var attempts = 0
        var locked = true
        val existing = context.getSharedPreferences("secure-factory-test", Context.MODE_PRIVATE)
        existing.edit().putString("preserved", "existing value").commit()
        val factory: () -> SharedPreferences = {
            attempts++
            if (locked) throw IllegalStateException("Keystore unavailable")
            existing
        }
        val tokens = GithubSecureTokenStore(context, preferencesFactory = factory)
        val cache = GithubEncryptedCacheStore(context, preferencesFactory = factory)
        val pending = GithubEncryptedPendingOAuthStore(context, preferencesFactory = factory)
        assertEquals(0, attempts)
        assertTrue(runCatching { tokens.read() }.isFailure)
        assertTrue(runCatching { cache.read("test") }.isFailure)
        assertTrue(runCatching { pending.read() }.isFailure)
        assertEquals(3, attempts)
        assertEquals("existing value", existing.getString("preserved", null))
        locked = false
        tokens.read(); cache.read("test"); pending.read()
        assertEquals(6, attempts)
        tokens.read(); cache.read("test"); pending.read()
        assertEquals(6, attempts)
        assertEquals("existing value", existing.getString("preserved", null))
    }
}
