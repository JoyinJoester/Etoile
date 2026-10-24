package takagi.ru.monica.github.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import takagi.ru.monica.github.domain.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class GithubPreferencesActionJournalTest {
    @Test fun durableMarkersReopenOnWorkerAndContainNoRawAccountRepositoryOrInputs() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("journal-test", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        val callerThread = Thread.currentThread()
        var storageThread: Thread? = null
        fun journal() = GithubPreferencesActionJournal(context, preferencesFactory = {
            storageThread = Thread.currentThread(); preferences
        })
        val scope = GithubActionScope(987654321L, "private-owner", "private-repository")
        val operation = GithubActionOperation(GithubActionKind.DISPATCH, 7)
        val first = journal()
        first.finish(first.begin(scope, operation, false), false)
        assertNotSame(callerThread, storageThread)
        val raw = preferences.all.toString()
        for (privateValue in listOf("987654321", "private-owner", "private-repository", "input", "token")) {
            assertFalse(raw.contains(privateValue))
        }
        val cold = journal()
        assertEquals(setOf(operation), cold.observe(scope).first())
        assertTrue(cold.observe(scope.copy(accountId = 2)).first().isEmpty())
        assertTrue(runCatching { cold.begin(scope, operation, false) }.isFailure)
        cold.acknowledge(scope, operation)
        assertTrue(journal().observe(scope).first().isEmpty())
    }
}
