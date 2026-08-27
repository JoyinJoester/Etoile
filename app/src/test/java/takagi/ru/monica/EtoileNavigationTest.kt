package takagi.ru.monica

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.github.navigation.GithubDestination
import takagi.ru.monica.github.navigation.GithubIssueRoute
import takagi.ru.monica.github.navigation.GithubActionsJobRoute
import takagi.ru.monica.github.navigation.GithubActionsRunRoute
import takagi.ru.monica.github.navigation.GithubPullRequestRoute
import takagi.ru.monica.github.navigation.GithubRepositoryActionsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryFilesRoute
import takagi.ru.monica.github.navigation.GithubRepositoryPullRequestsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryRoute
import takagi.ru.monica.github.navigation.GithubWorkflowRunsRoute

class EtoileNavigationTest {
    @Test
    fun primaryNavigationKeepsInboxExploreProfileOrder() {
        assertEquals(
            listOf(GithubDestination.INBOX, GithubDestination.EXPLORE, GithubDestination.PROFILE),
            GithubDestination.entries
        )
    }

    @Test
    fun repositoryRouteKeepsOwnerAndNameAsTypedCoordinates() {
        val route = GithubRepositoryRoute("openai/codex")

        assertEquals("openai", route.owner)
        assertEquals("codex", route.name)
    }

    @Test
    fun repositoryFilesRoutePreservesBranchAndNestedPath() {
        val route = GithubRepositoryFilesRoute("openai/codex", "main", "app/src")

        assertEquals("openai", route.owner)
        assertEquals("codex", route.name)
        assertEquals("main", route.ref)
        assertEquals("app/src", route.path)
    }

    @Test
    fun issueRouteKeepsRepositoryAndIssueNumber() {
        val route = GithubIssueRoute("openai/codex", 42)

        assertEquals("openai", route.owner)
        assertEquals("codex", route.name)
        assertEquals(42, route.number)
    }

    @Test
    fun pullRequestRoutesKeepRepositoryAndPullRequestNumber() {
        val listRoute = GithubRepositoryPullRequestsRoute("openai/codex")
        val detailRoute = GithubPullRequestRoute("openai/codex", 84)

        assertEquals("openai", listRoute.owner)
        assertEquals("codex", listRoute.name)
        assertEquals("openai", detailRoute.owner)
        assertEquals("codex", detailRoute.name)
        assertEquals(84, detailRoute.number)
    }

    @Test
    fun actionsRoutesKeepLongIdentifiersAndWorkflowContext() {
        val actionsRoute = GithubRepositoryActionsRoute("openai/codex")
        val workflowRoute = GithubWorkflowRunsRoute("openai/codex", 11L, "Android CI")
        val runRoute = GithubActionsRunRoute("openai/codex", 501L)
        val jobRoute = GithubActionsJobRoute("openai/codex", 701L)

        assertEquals("openai", actionsRoute.owner)
        assertEquals("codex", workflowRoute.name)
        assertEquals(11L, workflowRoute.workflowId)
        assertEquals("Android CI", workflowRoute.workflowName)
        assertEquals(501L, runRoute.runId)
        assertEquals(701L, jobRoute.jobId)
    }
}
