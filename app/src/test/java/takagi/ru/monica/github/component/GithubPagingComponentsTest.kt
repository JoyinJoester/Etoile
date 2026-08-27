package takagi.ru.monica.github.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubPagingComponentsTest {
    @Test
    fun errorHasPriorityOverExistingItemsAndPagination() {
        assertEquals(
            GithubPagedListIndicator.ERROR,
            indicator(itemCount = 20, isLoadingMore = true, hasError = true, canLoadMore = true)
        )
    }

    @Test
    fun emptyIsOnlyShownAfterInitialLoadingFinishes() {
        assertEquals(
            GithubPagedListIndicator.NONE,
            indicator(itemCount = 0, isInitialLoading = true)
        )
        assertEquals(
            GithubPagedListIndicator.EMPTY,
            indicator(itemCount = 0, isInitialLoading = false)
        )
        assertEquals(
            GithubPagedListIndicator.EMPTY_WITH_LOAD_MORE,
            indicator(itemCount = 0, isInitialLoading = false, canLoadMore = true)
        )
    }

    @Test
    fun paginationProgressAndActionUseDistinctIndicators() {
        assertEquals(
            GithubPagedListIndicator.LOADING_MORE,
            indicator(itemCount = 20, isLoadingMore = true)
        )
        assertEquals(
            GithubPagedListIndicator.LOAD_MORE,
            indicator(itemCount = 20, canLoadMore = true)
        )
        assertEquals(
            GithubPagedListIndicator.NONE,
            indicator(itemCount = 20)
        )
    }

    private fun indicator(
        itemCount: Int,
        isInitialLoading: Boolean = false,
        isLoadingMore: Boolean = false,
        hasError: Boolean = false,
        canLoadMore: Boolean = false
    ) = githubPagedListIndicator(
        itemCount = itemCount,
        isInitialLoading = isInitialLoading,
        isLoadingMore = isLoadingMore,
        hasError = hasError,
        canLoadMore = canLoadMore
    )
}
