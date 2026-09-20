package takagi.ru.monica.github.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubWebUrlsTest {
    @Test
    fun newIssueTemplateNamesCannotInjectQueryParametersOrFragments() {
        assertEquals(
            "https://github.com/openai/codex/issues/new?template=bug%20%26%20feature%3F%23.yml",
            GithubWebUrls.newIssue("openai/codex", "bug & feature?#.yml")
        )
        assertEquals("https://github.com/openai/codex/issues/new/choose", GithubWebUrls.issueTemplateChooser("openai/codex"))
    }

    private fun resolve(target: String) = GithubWebUrls.resolveMarkdownLink(
        "openai/codex", "main", "docs/README.md", target
    )

    @Test
    fun markdownExternalSchemesAndNetworkPathsAreNotRepositoryFiles() {
        assertEquals("mailto:maintainer@example.com", resolve("mailto:maintainer@example.com"))
        assertEquals("HTTPS://example.com/guide", resolve("HTTPS://example.com/guide"))
        assertEquals("https://example.com/guide", resolve("//example.com/guide"))
    }

    @Test
    fun markdownFileLinksKeepEscapingQueryAndAnchor() {
        assertEquals(
            "https://github.com/openai/codex/blob/main/docs/Guide%20One.md?raw=1&name=a%2Fb#part%202",
            resolve("Guide%20One.md?raw=1&name=a%2Fb#part%202")
        )
    }

    @Test
    fun markdownQueryOnlyAndEmptyTargetsStayOnCurrentFile() {
        assertEquals("https://github.com/openai/codex/blob/main/docs/README.md?raw=1#usage", resolve("?raw=1#usage"))
        assertEquals("https://github.com/openai/codex/blob/main/docs/README.md", resolve(""))
        assertEquals("https://github.com/openai/codex/blob/main/docs/README.md#part%202", resolve("#part%202"))
    }

    @Test
    fun markdownParentTraversalStaysWithinRepositoryRef() {
        assertEquals("https://github.com/openai/codex/blob/main/LICENSE", resolve("../../../LICENSE"))
    }
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
    fun individualWebhookUrlIncludesId() {
        assertEquals(
            "https://github.com/openai/codex/settings/hooks/108",
            GithubWebUrls.repositoryWebhookSettings("openai/codex", 108L)
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
    fun comparisonRangesKeepTheirDotsAndEncodeBranchSlashes() {
        assertEquals(
            "https://github.com/openai/codex/compare/main...feature/mobile",
            GithubWebUrls.compare("openai/codex", "main", "feature/mobile")
        )
        assertEquals(
            "https://github.com/openai/codex/compare/release/1.0...feature%20mobile",
            GithubWebUrls.compare("openai/codex", "release/1.0", "feature mobile")
        )
    }

    @Test
    fun externalProfileLinksNormalizeHttpUrlsAndRejectUnknownSchemes() {
        assertEquals("https://joyins.dev", GithubWebUrls.external("joyins.dev"))
        assertEquals("http://joyins.dev/docs", GithubWebUrls.external("http://joyins.dev/docs"))
        assertEquals(null, GithubWebUrls.external("javascript:alert(1)"))
    }
}
