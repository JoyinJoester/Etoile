package takagi.ru.monica.github.feature.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubRepositoryWebhook

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryCollaboratorsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun collaboratorsAppendWithoutDuplicatesAndCanBeFiltered() = runTest(dispatcher) {
        val viewModel = RepositoryCollaboratorsViewModel("openai", "codex", FakeRepository())
        advanceUntilIdle()

        viewModel.onAction(RepositoryCollaboratorsAction.LoadMore)
        advanceUntilIdle()
        viewModel.onAction(RepositoryCollaboratorsAction.Search("bob"))

        assertEquals(listOf("alice", "bob"), viewModel.state.value.items.map { it.user.login })
        assertEquals(listOf("bob"), viewModel.state.value.filteredItems.map { it.user.login })
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    private class FakeRepository : GithubRepositoryDetailsRepository {
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) = when (page) {
            1 -> Result.success(GithubPage(listOf(collaborator("alice", GithubCollaboratorRole.ADMIN)), 2))
            else -> Result.success(
                GithubPage(
                    listOf(
                        collaborator("alice", GithubCollaboratorRole.ADMIN),
                        collaborator("bob", GithubCollaboratorRole.WRITE)
                    ),
                    null
                )
            )
        }

        override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> =
            Result.failure(UnsupportedOperationException())

        override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)

        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            Result.success<GithubBranchProtection?>(null)

        override suspend fun updateTopics(owner: String, name: String, topics: List<String>) =
            Result.success(topics)

        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
    }

    private companion object {
        fun collaborator(login: String, role: GithubCollaboratorRole) = GithubCollaborator(
            user = GithubUserSummary(login, null, "https://github.com/$login"),
            role = role
        )
    }
}
