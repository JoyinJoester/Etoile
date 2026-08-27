package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubPageTest {
    @Test
    fun resetReplacesExistingItemsAndDeduplicatesThePage() {
        val page = GithubPage(items = listOf(Item(1), Item(1), Item(2)), nextPage = 2)

        val result = page.mergeItems(existing = listOf(Item(99)), reset = true, keySelector = Item::id)

        assertEquals(listOf(Item(1), Item(2)), result)
    }

    @Test
    fun appendKeepsExistingOrderAndDropsDuplicatePageItems() {
        val page = GithubPage(items = listOf(Item(2), Item(3), Item(3)), nextPage = null)

        val result = page.mergeItems(existing = listOf(Item(1), Item(2)), reset = false, keySelector = Item::id)

        assertEquals(listOf(Item(1), Item(2), Item(3)), result)
    }

    private data class Item(val id: Int)
}
