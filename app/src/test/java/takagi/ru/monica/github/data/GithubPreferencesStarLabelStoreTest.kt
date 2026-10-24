package takagi.ru.monica.github.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GithubPreferencesStarLabelStoreTest {
    @Test fun accountHandlesStayIsolatedAndPersistAcrossStoreRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val root = GithubPreferencesStarLabelStore(context)
        val first = root.forAccount(1)
        val second = root.forAccount(2)
        val privateLabel = first.createLabel("Private").getOrThrow()
        first.toggleAssignment(42, privateLabel.id)
        assertTrue(second.labels().isEmpty())
        val otherLabel = second.createLabel("Other").getOrThrow()
        second.toggleAssignment(42, otherLabel.id)
        assertEquals(listOf(privateLabel), first.labelsFor(42))
        assertEquals(listOf(otherLabel), second.labelsFor(42))
        val recreated = GithubPreferencesStarLabelStore(context)
        assertEquals(listOf(privateLabel), recreated.forAccount(1).labelsFor(42))
        assertEquals(listOf(otherLabel), recreated.forAccount(2).labelsFor(42))
    }

    @Test fun upgradeClaimsExistingLabelsForOneAccountOnly() {
        val root = GithubPreferencesStarLabelStore(RuntimeEnvironment.getApplication())
        val legacy = root.createLabel("My existing collection").getOrThrow()
        root.toggleAssignment(42, legacy.id)
        val first = root.forAccount(10)
        assertEquals(listOf(legacy), first.labelsFor(42))
        assertTrue(root.forAccount(20).labels().isEmpty())
        val recreated = GithubPreferencesStarLabelStore(RuntimeEnvironment.getApplication())
        assertEquals(listOf(legacy), recreated.forAccount(10).labelsFor(42))
        assertTrue(recreated.forAccount(30).labels().isEmpty())
    }
}
