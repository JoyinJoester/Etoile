package takagi.ru.monica.github.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubWebUrlsTest {
    @Test
    fun codeUrlsEncodeSegmentsWithoutLosingDirectoryStructure() {
        assertEquals(
            "https://github.com/openai/codex/blob/main/docs/Guide%20One.md",
            GithubWebUrls.blob("openai/codex", "main", "docs/Guide One.md")
        )
    }

    @Test
    fun markdownLinksResolveRelativeParentsAndFragments() {
        assertEquals(
            "https://github.com/openai/codex/blob/main/LICENSE#usage",
            GithubWebUrls.resolveMarkdownLink(
                fullName = "openai/codex",
                ref = "main",
                sourcePath = "docs/README.md",
                target = "../LICENSE#usage"
            )
        )
    }

    @Test
    fun issueUrlsUseNativeGithubIssuePaths() {
        assertEquals(
            "https://github.com/openai/codex/issues/42",
            GithubWebUrls.issue("openai/codex", 42)
        )
    }

    @Test
    fun pullRequestUrlsUseNativeGithubPullPaths() {
        assertEquals(
            "https://github.com/openai/codex/pulls",
            GithubWebUrls.pullRequests("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/pull/84",
            GithubWebUrls.pullRequest("openai/codex", 84)
        )
    }

    @Test
    fun actionsUrlsUseRepositoryAndRunPaths() {
        assertEquals(
            "https://github.com/openai/codex/actions",
            GithubWebUrls.actions("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/actions/runs/501",
            GithubWebUrls.actionsRun("openai/codex", 501L)
        )
    }

    @Test
    fun repositorySettingsUrlUsesTheNativeSettingsPath() {
        assertEquals(
            "https://github.com/openai/codex/settings",
            GithubWebUrls.repositorySettings("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/settings/branches",
            GithubWebUrls.repositoryBranchesSettings("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/settings/actions",
            GithubWebUrls.repositoryActionsSettings("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/settings/access",
            GithubWebUrls.repositoryCollaboratorsSettings("openai/codex")
        )
        assertEquals(
            "https://github.com/openai/codex/settings/hooks",
            GithubWebUrls.repositoryWebhooksSettings("openai/codex")
        )
    }

    @Test
    fun releasesUrlUsesTheRepositoryReleasePath() {
        assertEquals(
            "https://github.com/openai/codex/releases",
            GithubWebUrls.releases("openai/codex")
        )
    }

    @Test
    fun commitsUrlKeepsTheRepositoryRefPath() {
        assertEquals(
            "https://github.com/openai/codex/commits/main",
            GithubWebUrls.commits("openai/codex", "main")
        )
    }

    @Test
    fun userRepositoriesUrlKeepsTheNativeProfileTab() {
        assertEquals(
            "https://github.com/joyins?tab=repositories",
            GithubWebUrls.userRepositories("joyins")
        )
    }

    @Test
    fun publicUserUrlUsesTheNativeProfilePath() {
        assertEquals(
            "https://github.com/joyins",
            GithubWebUrls.user("joyins")
        )
    }

    @Test
    fun publicUserRelationshipUrlsUseProfileTabs() {
        assertEquals(
            "https://github.com/joyins?tab=followers",
            GithubWebUrls.userFollowers("joyins")
        )
        assertEquals(
            "https://github.com/joyins?tab=following",
            GithubWebUrls.userFollowing("joyins")
        )
    }

    @Test
    fun externalProfileLinksNormalizeHttpUrlsAndRejectUnknownSchemes() {
        assertEquals("https://joyins.dev", GithubWebUrls.external("joyins.dev"))
        assertEquals("http://joyins.dev/docs", GithubWebUrls.external("http://joyins.dev/docs"))
        assertEquals(null, GithubWebUrls.external("javascript:alert(1)"))
    }
}
