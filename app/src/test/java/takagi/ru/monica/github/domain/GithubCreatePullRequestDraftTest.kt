package takagi.ru.monica.github.domain

import org.junit.Assert.*
import org.junit.Test

class GithubCreatePullRequestDraftTest {
    @Test fun supportsForksDraftsAndAnEmptyDescription() {
        val draft = GithubCreatePullRequestDraft.fromInput(" Title ", "", " main ", "alice:feature/mobile", true, true, "fork").getOrThrow()
        assertEquals("Title", draft.title)
        assertEquals("main", draft.base)
        assertEquals("alice:feature/mobile", draft.head)
        assertEquals("fork", draft.headRepository)
        assertTrue(draft.draft)
        assertTrue(draft.maintainerCanModify)
    }

    @Test fun rejectsInvalidRefsAndIdenticalBranches() {
        for (head in listOf("", "main", "a b", "a..b", "a//b", "a.lock", ".private", "branch/", "owner:branch:other", "branch@{1}")) {
            assertTrue(head, GithubCreatePullRequestDraft.fromInput("Title", "", "main", head).isFailure)
        }
        assertTrue(GithubCreatePullRequestDraft.fromInput("Title", "", "main", "alice:main").isSuccess)
    }

    @Test fun enforcesTextLimitsAndQualifiedHeadRepository() {
        assertTrue(GithubCreatePullRequestDraft.fromInput(" ", "", "main", "feature").isFailure)
        assertTrue(GithubCreatePullRequestDraft.fromInput("x".repeat(257), "", "main", "feature").isFailure)
        assertTrue(GithubCreatePullRequestDraft.fromInput("Title", "x".repeat(65537), "main", "feature").isFailure)
        assertTrue(GithubCreatePullRequestDraft.fromInput("Title", "", "main", "feature", headRepository = "fork").isFailure)
        assertTrue(GithubCreatePullRequestDraft.fromInput("Title", "", "main", "alice:feature", headRepository = "alice/fork").isFailure)
    }
}
