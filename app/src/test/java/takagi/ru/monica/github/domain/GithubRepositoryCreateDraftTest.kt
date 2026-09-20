package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubRepositoryCreateDraftTest {
    @Test
    fun nameAcceptsOnlyWhatGithubAllows() {
        listOf("demo-app", "a_b.9", "Etoile").forEach {
            assertTrue(it, GithubRepositoryCreateDraft.isValidName(it))
        }
        listOf("", "   ", "bad name", "with/slash", "ünïcode", "a".repeat(GithubRepositoryCreateDraft.MAX_NAME_LENGTH + 1)).forEach {
            assertFalse(it, GithubRepositoryCreateDraft.isValidName(it))
        }
    }

    @Test
    fun fromInputTrimsTheNameAndFoldsABlankDescriptionToNothing() {
        val draft = GithubRepositoryCreateDraft.fromInput("  demo-app  ", "   ", false, true).getOrThrow()

        assertEquals("demo-app", draft.name)
        assertNull(draft.description)
        assertTrue(draft.autoInit)
    }

    @Test
    fun fromInputKeepsARealDescriptionTrimmedAndRefusesAnUnavailableName() {
        val draft = GithubRepositoryCreateDraft.fromInput("demo-app", "  a demo  ", true, false).getOrThrow()

        assertEquals("a demo", draft.description)
        assertTrue(draft.isPrivate)
        assertTrue(GithubRepositoryCreateDraft.fromInput("bad name", null, false, false).isFailure)
    }
}
