package takagi.ru.monica.github.domain

import org.junit.Assert.*
import org.junit.Test

class GithubFileWriteTest {
    @Test fun rejectsAmbiguousPathsAndDeletionWithoutSha() {
        for (path in listOf("", "/file", "a//b", "../file", "a/./b", "a\\b", "a\u0000b")) {
            assertTrue(path, GithubFileWrite.fromInput(path, "main", "Commit", "text", null).isFailure)
        }
        assertTrue(GithubFileWrite.fromInput("file", "main", "Delete", null, null).isFailure)
        assertTrue(GithubFileWrite.fromInput("file", "main", "Update", "text", "badsha").isFailure)
    }

    @Test fun limitsUtf8BytesAndRequiresCommitMessageAndBranch() {
        assertTrue(GithubFileWrite.fromInput("file", "", "Commit", "text", null).isFailure)
        assertTrue(GithubFileWrite.fromInput("file", "main", " ", "text", null).isFailure)
        assertTrue(GithubFileWrite.fromInput("file", "main", "Commit", "你".repeat(200000), null).isFailure)
        assertTrue(GithubFileWrite.fromInput("file", "main", "Commit", "", null).isSuccess)
    }
}
