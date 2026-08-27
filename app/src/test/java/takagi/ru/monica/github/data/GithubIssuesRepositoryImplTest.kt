package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubSortDirection

class GithubIssuesRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun issueListUsesPaginationAndFiltersPullRequestsFromTheIssuesEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Link",
                    "<${server.url("/repos/openai/codex/issues?page=2")}>; rel=\"next\", " +
                        "<${server.url("/repos/openai/codex/issues?page=4")}>; rel=\"last\""
                )
                .setBody(ISSUES_JSON)
        )
        val repository = repository()

        val page = repository.issues(
            "openai",
            "codex",
            GithubIssueListQuery(GithubIssueState.OPEN),
            page = 1,
            perPage = 30
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/issues?state=open&sort=updated&direction=desc&per_page=30&page=1", request.path)
        assertEquals(1, page.items.size)
        assertEquals(42, page.items.single().number)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun issueListHonorsCreatedAscendingOrdering() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val repository = repository()

        repository.issues(
            "openai",
            "codex",
            GithubIssueListQuery(
                state = GithubIssueState.CLOSED,
                sort = GithubListSort.CREATED,
                direction = GithubSortDirection.ASC
            ),
            page = 3,
            perPage = 50
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals(
            "/repos/openai/codex/issues?state=closed&sort=created&direction=asc&per_page=50&page=3",
            request.path
        )
    }

    @Test
    fun issueDetailAndCommentsMapMarkdownAndAuthorMetadata() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        server.enqueue(MockResponse().setResponseCode(200).setBody(COMMENTS_JSON))
        val repository = repository()

        val issue = repository.issue("openai", "codex", 42).getOrThrow()
        val issueRequest = server.takeRequest()
        val comments = repository.comments("openai", "codex", 42, page = 1, perPage = 100).getOrThrow()
        val commentsRequest = server.takeRequest()

        assertEquals("/repos/openai/codex/issues/42", issueRequest.path)
        assertEquals("## Reproduction", issue.body)
        assertEquals("alice", issue.author.login)
        assertEquals("/repos/openai/codex/issues/42/comments?per_page=100&page=1", commentsRequest.path)
        assertEquals("Looks reproducible", comments.items.single().body)
        assertNull(comments.nextPage)
    }

    @Test
    fun issueWritesRequireAuthenticationAndUseSerializedJsonBodies() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(ISSUE_JSON))
        server.enqueue(MockResponse().setResponseCode(201).setBody(COMMENTS_JSON.trim().removePrefix("[").removeSuffix("]").trim()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON.replace("\"state\": \"open\"", "\"state\": \"closed\"")))
        val repository = repository()

        repository.createIssue(
            "openai",
            "codex",
            GithubIssueDraft.fromInput("Crash on launch", "Steps").getOrThrow()
        ).getOrThrow()
        val createRequest = server.takeRequest()
        repository.addComment(
            "openai",
            "codex",
            42,
            GithubIssueCommentDraft.fromInput("Looks reproducible").getOrThrow()
        ).getOrThrow()
        val commentRequest = server.takeRequest()
        val updated = repository.updateIssueState("openai", "codex", 42, GithubIssueState.CLOSED).getOrThrow()
        val updateRequest = server.takeRequest()

        assertEquals("POST", createRequest.method)
        assertEquals("/repos/openai/codex/issues", createRequest.path)
        assertEquals("Bearer test_token_12345678901234567890", createRequest.getHeader("Authorization"))
        assertEquals("{\"title\":\"Crash on launch\",\"body\":\"Steps\"}", createRequest.body.readUtf8())
        assertEquals("POST", commentRequest.method)
        assertEquals("/repos/openai/codex/issues/42/comments", commentRequest.path)
        assertEquals("{\"body\":\"Looks reproducible\"}", commentRequest.body.readUtf8())
        assertEquals("PATCH", updateRequest.method)
        assertEquals("{\"state\":\"closed\"}", updateRequest.body.readUtf8())
        assertEquals(GithubIssueState.CLOSED, updated.state)
    }

    @Test
    fun issueLockUsesPutOrDeleteAndRefreshesIssue() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON.replace("\"locked\": false", "\"locked\": true")))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        val repository = repository()

        val locked = repository.updateIssueLock("openai", "codex", 42, locked = true).getOrThrow()
        val putRequest = server.takeRequest()
        val lockedRead = server.takeRequest()
        val unlocked = repository.updateIssueLock("openai", "codex", 42, locked = false).getOrThrow()
        val deleteRequest = server.takeRequest()
        server.takeRequest()

        assertEquals("PUT", putRequest.method)
        assertEquals("/repos/openai/codex/issues/42/lock", putRequest.path)
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/repos/openai/codex/issues/42/lock", deleteRequest.path)
        assertEquals("/repos/openai/codex/issues/42", lockedRead.path)
        assertTrue(locked.isLocked)
        assertFalse(unlocked.isLocked)
    }

    @Test
    fun labelsUsePaginationAndIssueUpdateRefreshesDetail() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/labels?page=2")}>; rel=\"next\"")
                .setBody("[{\"name\":\"bug\",\"color\":\"d73a4a\",\"description\":\"Problem\"}]")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        val repository = repository()

        val labels = repository.labels("openai", "codex", page = 1, perPage = 100).getOrThrow()
        val listRequest = server.takeRequest()
        repository.updateIssueLabels("openai", "codex", 42, listOf("bug", " bug ", "")).getOrThrow()
        val updateRequest = server.takeRequest()
        val refreshedRequest = server.takeRequest()

        assertEquals("/repos/openai/codex/labels?per_page=100&page=1", listRequest.path)
        assertEquals(2, labels.nextPage)
        assertEquals("bug", labels.items.single().name)
        assertEquals("PUT", updateRequest.method)
        assertEquals("/repos/openai/codex/issues/42/labels", updateRequest.path)
        assertEquals("{\"labels\":[\"bug\"]}", updateRequest.body.readUtf8())
        assertEquals("/repos/openai/codex/issues/42", refreshedRequest.path)
    }

    @Test
    fun assigneesUsePaginationAndUpdateIssueWithNormalizedLogins() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/assignees?page=2")}>; rel=\"next\"")
                .setBody("[{\"login\":\"alice\",\"avatar_url\":null,\"html_url\":\"https://github.com/alice\"}]")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        val repository = repository()

        val assignees = repository.assignees("openai", "codex", page = 1, perPage = 100).getOrThrow()
        val listRequest = server.takeRequest()
        repository.updateIssueAssignees("openai", "codex", 42, listOf(" alice ", "alice", "")).getOrThrow()
        val updateRequest = server.takeRequest()

        assertEquals("/repos/openai/codex/assignees?per_page=100&page=1", listRequest.path)
        assertEquals(2, assignees.nextPage)
        assertEquals("alice", assignees.items.single().login)
        assertEquals("PATCH", updateRequest.method)
        assertEquals("/repos/openai/codex/issues/42", updateRequest.path)
        assertEquals("{\"assignees\":[\"alice\"]}", updateRequest.body.readUtf8())
    }

    @Test
    fun milestonesUsePaginationAndCanBeAssignedOrCleared() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[{\"number\":3,\"title\":\"v1.0\",\"description\":\"Launch\",\"open_issues\":4,\"closed_issues\":6,\"due_on\":\"2026-09-01T00:00:00Z\"}]")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(issueWithMilestoneJson()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        val repository = repository()

        val milestones = repository.milestones("openai", "codex").getOrThrow()
        val listRequest = server.takeRequest()
        val assigned = repository.updateIssueMilestone("openai", "codex", 42, 3).getOrThrow()
        val assignRequest = server.takeRequest()
        val cleared = repository.updateIssueMilestone("openai", "codex", 42, null).getOrThrow()
        val clearRequest = server.takeRequest()

        assertEquals(
            "/repos/openai/codex/milestones?state=open&sort=due_on&direction=asc&per_page=100&page=1",
            listRequest.path
        )
        assertEquals("v1.0", milestones.items.single().title)
        assertEquals(4, milestones.items.single().openIssues)
        assertEquals("{\"milestone\":3}", assignRequest.body.readUtf8())
        assertEquals(3, assigned.milestone?.number)
        assertEquals("{\"milestone\":null}", clearRequest.body.readUtf8())
        assertNull(cleared.milestone)
    }

    @Test
    fun updateIssueContentUsesPatchAndMapsReturnedIssue() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ISSUE_JSON))
        val repository = repository()

        val updated = repository.updateIssue(
            "openai",
            "codex",
            42,
            GithubIssueDraft.fromInput("New title", "New body").getOrThrow()
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("/repos/openai/codex/issues/42", request.path)
        assertEquals("{\"title\":\"New title\",\"body\":\"New body\"}", request.body.readUtf8())
        assertEquals("Crash when opening repository", updated.title)
    }

    @Test
    fun issueDetailUsesEtagAndSuccessfulWriteInvalidatesCachedReads() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"issue-v1\"")
                .setBody(ISSUE_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(201).setBody(ISSUE_JSON))
        val cacheStore = TestGithubCacheStore()
        val repository = repository(cacheStore)

        repository.issue("openai", "codex", 42).getOrThrow()
        server.takeRequest()
        repository.issue("openai", "codex", 42).getOrThrow()
        val validationRequest = server.takeRequest()

        assertEquals("\"issue-v1\"", validationRequest.getHeader("If-None-Match"))
        assertFalse(cacheStore.isEmpty())

        repository.createIssue(
            "openai",
            "codex",
            GithubIssueDraft.fromInput("Crash on launch", "Steps").getOrThrow()
        ).getOrThrow()

        assertTrue(cacheStore.isEmpty())
    }

    @Test
    fun commentReactionToggleAddsReactionAfterCheckingViewerReactions() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":501,"content":"+1"}"""))
        val repository = repository()

        val result = repository.toggleCommentReaction(
            owner = "openai",
            name = "codex",
            commentId = 77L,
            content = GithubReactionContent.PLUS_ONE,
            viewerLogin = "joyins"
        ).getOrThrow()
        val checkRequest = server.takeRequest()
        val addRequest = server.takeRequest()

        assertEquals("/repos/openai/codex/issues/comments/77/reactions", checkRequest.path)
        assertEquals("GET", checkRequest.method)
        assertEquals("POST", addRequest.method)
        assertEquals("{\"content\":\"+1\"}", addRequest.body.readUtf8())
        assertEquals(true, result.active)
        assertEquals(501L, result.reactionId)
    }

    @Test
    fun commentReactionToggleRemovesViewerReactionByReactionId() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":502,"content":"heart","user":{"login":"joyins"}}]"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository()

        val result = repository.toggleCommentReaction(
            owner = "openai",
            name = "codex",
            commentId = 77L,
            content = GithubReactionContent.HEART,
            viewerLogin = "joyins"
        ).getOrThrow()
        server.takeRequest()
        val deleteRequest = server.takeRequest()

        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/repos/openai/codex/issues/comments/77/reactions/502", deleteRequest.path)
        assertEquals(false, result.active)
        assertEquals(502L, result.reactionId)
    }

    private fun repository(cacheStore: GithubCacheStore = NoOpGithubCacheStore) = GithubIssuesRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        fun issueWithMilestoneJson() = ISSUE_JSON.replace(
            "\"labels\":",
            "\"milestone\": {\"number\":3,\"title\":\"v1.0\",\"description\":\"Launch\",\"open_issues\":4,\"closed_issues\":6,\"due_on\":\"2026-09-01T00:00:00Z\"},\n  \"labels\":"
        )

        val ISSUE_JSON = """
            {
              "id": 100,
              "number": 42,
              "title": "Crash when opening repository",
              "body": "## Reproduction",
              "state": "open",
              "locked": false,
              "comments": 1,
              "created_at": "2026-08-15T00:00:00Z",
              "updated_at": "2026-08-16T00:00:00Z",
              "closed_at": null,
              "html_url": "https://github.com/openai/codex/issues/42",
              "user": { "login": "alice", "avatar_url": "https://avatars.example/alice", "html_url": "https://github.com/alice" },
              "labels": [{ "name": "bug", "color": "d73a4a", "description": "Something is broken" }],
              "assignees": []
            }
        """.trimIndent()

        val ISSUES_JSON = """
            [
              $ISSUE_JSON,
              {
                "id": 101,
                "number": 43,
                "title": "A pull request returned by the issues endpoint",
                "body": null,
                "state": "open",
                "locked": false,
                "comments": 0,
                "created_at": "2026-08-15T00:00:00Z",
                "updated_at": "2026-08-16T00:00:00Z",
                "closed_at": null,
                "html_url": "https://github.com/openai/codex/pull/43",
                "user": { "login": "bob", "avatar_url": null, "html_url": "https://github.com/bob" },
                "labels": [],
                "assignees": [],
                "pull_request": { "url": "https://api.github.com/repos/openai/codex/pulls/43" }
              }
            ]
        """.trimIndent()

        val COMMENTS_JSON = """
            [
              {
                "id": 501,
                "body": "Looks reproducible",
                "created_at": "2026-08-16T01:00:00Z",
                "updated_at": "2026-08-16T01:00:00Z",
                "html_url": "https://github.com/openai/codex/issues/42#issuecomment-501",
                "user": { "login": "maintainer", "avatar_url": null, "html_url": "https://github.com/maintainer" }
              }
            ]
        """.trimIndent()
    }
}
