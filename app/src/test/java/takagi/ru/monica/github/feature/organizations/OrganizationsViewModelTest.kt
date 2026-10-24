package takagi.ru.monica.github.feature.organizations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubOrganization
import takagi.ru.monica.github.domain.GithubOrganizationsRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.signedInGithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun backgroundSessionCollectorClearsOrganizationsAndIgnoresLateReads() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<GithubPage<GithubOrganization>>>()
        var requests = 0
        val repository = object : GithubOrganizationsRepository {
            override suspend fun myOrganizations(page: Int, perPage: Int): Result<GithubPage<GithubOrganization>> {
                requests++
                return if (requests == 1) withContext(NonCancellable) { gate.await() }
                else Result.success(GithubPage(listOf(GithubOrganization(2, "new-org", null, null)), null))
            }
        }
        val alice = signedInGithubSession("alice")
        val sessions = MutableStateFlow<GithubSession>(alice)
        val viewModel = OrganizationsViewModel(repository, sessions, expectedAccountId = 1)
        runCurrent()
        sessions.value = GithubSession.SignedIn(alice.account.copy(id = 2))
        runCurrent()
        gate.complete(Result.success(GithubPage(listOf(GithubOrganization(1, "private-org", null, null)), null)))
        advanceUntilIdle()
        assertEquals(emptyList<GithubOrganization>(), viewModel.state.value.items)
        viewModel.onAction(OrganizationsAction.Refresh)
        advanceUntilIdle()
        assertEquals(1, requests)
        assertFalse(viewModel.state.value.isLoading)
        sessions.value = alice
        advanceUntilIdle()
        assertEquals(listOf("new-org"), viewModel.state.value.items.map { it.login })
    }
}
