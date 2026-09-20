package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubStarLabelValidationTest {
    @Test
    fun namesAreTrimmedBeforeBeingAccepted() {
        assertEquals("Android", validateStarLabelName("  Android  ", emptyList()).getOrNull())
    }

    @Test
    fun blankAndOverlongNamesAreRejected() {
        assertEquals(
            GithubStarLabelError.BLANK_NAME,
            validateStarLabelName("   ", emptyList()).labelError()
        )
        assertEquals(
            GithubStarLabelError.NAME_TOO_LONG,
            validateStarLabelName("x".repeat(MAX_STAR_LABEL_NAME_LENGTH + 1), emptyList()).labelError()
        )
    }

    @Test
    fun duplicateNamesAreRejectedRegardlessOfCase() {
        val existing = listOf(GithubStarLabel(1, "Kotlin"))
        assertEquals(
            GithubStarLabelError.DUPLICATE_NAME,
            validateStarLabelName("kotlin", existing).labelError()
        )
    }

    @Test
    fun renamingALabelToItsOwnNameStaysValid() {
        val existing = listOf(GithubStarLabel(1, "Kotlin"))
        assertEquals(
            "Kotlin",
            validateStarLabelName("Kotlin", existing, ignoreLabelId = 1).getOrNull()
        )
    }

    @Test
    fun filtersSelectByLabelMembership() {
        val android = GithubStarLabel(1, "Android")
        val tools = GithubStarLabel(2, "Tools")
        val labeled = GithubLabeledStar(repository(), listOf(android, tools))
        val unlabeled = GithubLabeledStar(repository(id = 2), emptyList())

        assertTrue(labeled.matches(GithubStarFilter.All))
        assertTrue(labeled.matches(GithubStarFilter.Label(tools.id)))
        assertFalse(labeled.matches(GithubStarFilter.Label(99)))
        assertFalse(labeled.matches(GithubStarFilter.Unlabeled))
        assertTrue(unlabeled.matches(GithubStarFilter.Unlabeled))
    }

    private fun Result<String>.labelError() =
        (exceptionOrNull() as? GithubStarLabelException)?.error

    private fun repository(id: Long = 1) = GithubRepository(
        id,
        "etoile-$id",
        "joyins/etoile-$id",
        null,
        null,
        0,
        null,
        false,
        "https://github.com/joyins/etoile-$id"
    )
}
