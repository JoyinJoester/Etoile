package takagi.ru.monica.github.feature.starred

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
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarCategory
import takagi.ru.monica.github.domain.GithubStarCategoryStore
import takagi.ru.monica.github.domain.GithubStarsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class StarredViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun signedInSessionLoadsRepositoriesWithPersistedCategories() = runTest(dispatcher) {
        val categoryStore = FakeCategoryStore(mutableMapOf(1L to GithubStarCategory.KOTLIN))
        val viewModel = StarredViewModel(FakeStarsRepository(), categoryStore)

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(GithubStarCategory.KOTLIN, viewModel.state.value.repositories.single().category)
    }

    @Test
    fun assigningCategoryPersistsAndUpdatesVisibleState() = runTest(dispatcher) {
        val categoryStore = FakeCategoryStore()
        val viewModel = StarredViewModel(FakeStarsRepository(), categoryStore)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.RepositoryCategorized(1, GithubStarCategory.ANDROID))

        assertEquals(GithubStarCategory.ANDROID, categoryStore.category(1))
        assertEquals(GithubStarCategory.ANDROID, viewModel.state.value.repositories.single().category)
    }

    @Test
    fun loadMoreAppendsStarredRepositoriesAndCategoriesEachPage() = runTest(dispatcher) {
        val categoryStore = FakeCategoryStore(mutableMapOf(2L to GithubStarCategory.TOOLS))
        val viewModel = StarredViewModel(FakeStarsRepository(), categoryStore)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(StarredAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.state.value.repositories.map { it.repository.id })
        assertEquals(GithubStarCategory.TOOLS, viewModel.state.value.repositories.last().category)
    }

    private class FakeStarsRepository : GithubStarsRepository {
        override suspend fun starredRepositories(page: Int, perPage: Int) = Result.success(
            GithubPage(
                items = listOf(repository(page.toLong())),
                nextPage = if (page == 1) 2 else null
            )
        )
    }

    private class FakeCategoryStore(private val categories: MutableMap<Long, GithubStarCategory> = mutableMapOf()) : GithubStarCategoryStore {
        override fun category(repositoryId: Long) = categories[repositoryId] ?: GithubStarCategory.ALL
        override fun setCategory(repositoryId: Long, category: GithubStarCategory) { categories[repositoryId] = category }
    }

    private companion object {
        fun repository(id: Long = 1) = GithubRepository(id, "etoile-$id", "joyins/etoile-$id", "GitHub client", "Kotlin", 100, "2026-08-16", false, "https://github.com/joyins/etoile-$id")
    }
    private fun account() = GithubAccount(1, "joyins", "Joyins", null, "", "https://github.com/joyins", 1, 1, 1)
}
