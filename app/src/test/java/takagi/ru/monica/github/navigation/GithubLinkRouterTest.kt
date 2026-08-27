package takagi.ru.monica.github.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubLinkRouterTest {
    @Test
    fun parsesNativeRepositoryAndWorkItemLinks() {
        assertEquals(
            GithubLinkDestination.Repository("etoile/mobile"),
            GithubLinkRouter.parse("https://github.com/etoile/mobile")
        )
        assertEquals(
            GithubLinkDestination.Issue("etoile/mobile", 42),
            GithubLinkRouter.parse("https://www.github.com/etoile/mobile/issues/42?ref=notifications")
        )
        assertEquals(
            GithubLinkDestination.PullRequest("etoile/mobile", 7),
            GithubLinkRouter.parse("https://github.com/etoile/mobile/pull/7#discussion")
        )
    }

    @Test
    fun parsesActionsRunAndJobLinks() {
        assertEquals(
            GithubLinkDestination.ActionsRun("etoile/mobile", 123456L),
            GithubLinkRouter.parse("https://github.com/etoile/mobile/actions/runs/123456")
        )
        assertEquals(
            GithubLinkDestination.ActionsJob("etoile/mobile", 654321L),
            GithubLinkRouter.parse("https://github.com/etoile/mobile/actions/jobs/654321")
        )
    }

    @Test
    fun parsesReleaseListAndTagLinks() {
        assertEquals(
            GithubLinkDestination.Releases("etoile/mobile"),
            GithubLinkRouter.parse("https://github.com/etoile/mobile/releases")
        )
        assertEquals(
            GithubLinkDestination.ReleaseTag("etoile/mobile", "preview/1.2"),
            GithubLinkRouter.parse("https://github.com/etoile/mobile/releases/tag/preview/1.2")
        )
    }

    @Test
    fun parsesCommitLinks() {
        assertEquals(
            GithubLinkDestination.Commit(
                "etoile/mobile",
                "abcdef1234567890abcdef1234567890abcdef12"
            ),
            GithubLinkRouter.parse(
                "https://github.com/etoile/mobile/commit/abcdef1234567890abcdef1234567890abcdef12"
            )
        )
    }

    @Test
    fun publicUserLinksUseNativeProfileRoute() {
        assertEquals(
            GithubLinkDestination.User("joyins"),
            GithubLinkRouter.parse("https://github.com/joyins")
        )
    }

    @Test
    fun publicUserRelationshipTabsUseNativeLists() {
        assertEquals(
            GithubLinkDestination.UserFollowers("joyins"),
            GithubLinkRouter.parse("https://github.com/joyins?tab=followers")
        )
        assertEquals(
            GithubLinkDestination.UserFollowing("joyins"),
            GithubLinkRouter.parse("https://github.com/joyins?tab=following")
        )
    }

    @Test
    fun rejectsUnsupportedOrMalformedLinks() {
        assertNull(GithubLinkRouter.parse("https://example.com/etoile/mobile/issues/1"))
        assertNull(GithubLinkRouter.parse("https://github.com/etoile/mobile/pulls"))
        assertNull(GithubLinkRouter.parse("https://github.com/etoile/mobile/issues/0"))
        assertNull(GithubLinkRouter.parse("not a url"))
    }
}
