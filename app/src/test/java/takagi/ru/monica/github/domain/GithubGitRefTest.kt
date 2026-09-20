package takagi.ru.monica.github.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubGitRefTest {
    @Test fun acceptsRealBranchNamesIncludingUnicodeAndNestedPaths() {
        for (name in listOf(
            "main",
            "feature/android",
            "release/2026-09-19",
            "v1.0",
            "修复登录",
            "a".repeat(40)
        )) {
            assertTrue(name, GithubGitRef.isValidBranchName(name))
        }
    }

    @Test fun rejectsNamesGitWouldRefuse() {
        for (name in listOf(
            "",
            " ",
            "feature android",
            "feature\nbranch",
            "-feature",
            "@",
            "@{1}",
            "feature~1",
            "refs^0",
            "origin/main:",
            "feature?abc",
            "feature*star",
            "feature[bracket",
            "feature\\slash",
            "a..b",
            "feature.lock",
            ".hidden",
            "trailing.",
            "feature//nested",
            "feature/",
            "/feature",
            "x".repeat(GithubGitRef.MAX_BRANCH_NAME_LENGTH + 1)
        )) {
            assertFalse(name, GithubGitRef.isValidBranchName(name))
        }
    }
}
