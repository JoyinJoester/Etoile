package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubMergeMethod
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubPullRequestListQuery
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubRequestedReviewersUpdate
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubReviewEvent
import takagi.ru.monica.github.domain.GithubReviewState
import takagi.ru.monica.github.domain.GithubSortDirection

class GithubPullRequestsRepositoryImplTest {
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
    fun pullRequestListUsesPaginationAndMapsBranchMetadata() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/pulls?page=2")}>; rel=\"next\"")
                .setBody("[$PULL_REQUEST_JSON]")
        )
        val repository = repository()

        val page = repository.pullRequests(
            "openai",
            "codex",
            GithubPullRequestListQuery(GithubPullRequestState.OPEN),
            1,
            30
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/pulls?state=open&sort=updated&direction=desc&per_page=30&page=1", request.path)
        assertEquals(2, page.nextPage)
        assertEquals("feature/native-pr", page.items.single().head.ref)
        assertEquals("main", page.items.single().base.ref)
        assertTrue(page.items.single().isDraft)
    }

    @Test
    fun pullRequestListHonorsCreatedAscendingOrdering() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val repository = repository()

        repository.pullRequests(
            "openai",
            "codex",
            GithubPullRequestListQuery(
                state = GithubPullRequestState.CLOSED,
                sort = GithubListSort.CREATED,
                direction = GithubSortDirection.ASC
            ),
            page = 3,
            perPage = 50
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals(
            "/repos/openai/codex/pulls?state=closed&sort=created&direction=asc&per_page=50&page=3",
            request.path
        )
    }

    @Test
    fun detailFilesAndReviewsMapDiffAndReviewState() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(PULL_REQUEST_JSON))
        server.enqueue(MockResponse().setResponseCode(200).setBody(FILES_JSON))
        server.enqueue(MockResponse().setResponseCode(200).setBody(REVIEWS_JSON))
        val repository = repository()

        val pullRequest = repository.pullRequest("openai", "codex", 7).getOrThrow()
        server.takeRequest()
        val files = repository.files("openai", "codex", 7, 1, 100).getOrThrow()
        val filesRequest = server.takeRequest()
        val reviews = repository.reviews("openai", "codex", 7, 1, 100).getOrThrow()
        val reviewsRequest = server.takeRequest()

        assertEquals(12, pullRequest.additions)
        assertEquals(3, pullRequest.deletions)
        assertTrue(pullRequest.isLocked)
        assertEquals(5, pullRequest.milestone?.number)
        assertEquals("/repos/openai/codex/pulls/7/files?per_page=100&page=1", filesRequest.path)
        assertEquals("@@ -1 +1 @@\n-old\n+new", files.items.single().patch)
        assertEquals("/repos/openai/codex/pulls/7/reviews?per_page=100&page=1", reviewsRequest.path)
        assertEquals(GithubReviewState.APPROVED, reviews.items.single().state)
    }

    @Test
    fun reviewCommentsUsePullRequestCommentsEndpointAndMapLineContext() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(REVIEW_COMMENTS_JSON)
        )
        val repository = repository()

        val page = repository.reviewComments("openai", "codex", 7, 1, 100).getOrThrow()
        val request = server.takeRequest()
        val comment = page.items.single()

        assertEquals("/repos/openai/codex/pulls/7/comments?per_page=100&page=1", request.path)
        assertEquals(901L, comment.id)
        assertEquals("app/Main.kt", comment.path)
        assertEquals(42, comment.line)
        assertEquals("alice", comment.author.login)
        assertEquals("@@ -40 +40 @@", comment.diffHunk)
    }

    @Test
    fun mergedTimestampMarksClosedPullRequestAsMergedWhenListPayloadOmitsMergedFlag() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "[${PULL_REQUEST_JSON.replace("\"state\": \"open\"", "\"state\": \"closed\"")
                    .replace("\"merged_at\": null", "\"merged_at\": \"2026-08-16T03:00:00Z\"")}]"
            )
        )
        val repository = repository()

        val pullRequest = repository.pullRequests(
            "openai",
            "codex",
            GithubPullRequestListQuery(GithubPullRequestState.CLOSED),
            1,
            30
        ).getOrThrow().items.single()

        assertTrue(pullRequest.isMerged)
    }

    @Test
    fun reviewMergeAndStateWritesRequireAuthenticationAndUseSerializedBodies() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(REVIEWS_JSON.trim().removePrefix("[").removeSuffix("]").trim()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(MERGE_JSON))
        server.enqueue(MockResponse().setResponseCode(200).setBody(PULL_REQUEST_JSON.replace("\"state\": \"open\"", "\"state\": \"closed\"")))
        val repository = repository()

        repository.submitReview(
            "openai",
            "codex",
            7,
            GithubPullRequestReviewDraft.fromInput(GithubReviewEvent.APPROVE, "Ship it").getOrThrow()
        ).getOrThrow()
        val reviewRequest = server.takeRequest()
        val mergeResult = repository.merge(
            "openai",
            "codex",
            7,
            GithubMergeDraft.fromInput(
                method = GithubMergeMethod.SQUASH,
                expectedHeadSha = "head-sha",
                commitTitle = "Native client (#7)",
                commitMessage = "Ship the native pull request workflow"
            ).getOrThrow()
        ).getOrThrow()
        val mergeRequest = server.takeRequest()
        repository.updateState("openai", "codex", 7, GithubPullRequestState.CLOSED).getOrThrow()
        val stateRequest = server.takeRequest()

        assertEquals("POST", reviewRequest.method)
        assertEquals("Bearer test_token_12345678901234567890", reviewRequest.getHeader("Authorization"))
        assertEquals("{\"body\":\"Ship it\",\"event\":\"APPROVE\"}", reviewRequest.body.readUtf8())
        assertEquals("PUT", mergeRequest.method)
        assertEquals(
            "{\"sha\":\"head-sha\",\"merge_method\":\"squash\",\"commit_title\":\"Native client (#7)\",\"commit_message\":\"Ship the native pull request workflow\"}",
            mergeRequest.body.readUtf8()
        )
        assertTrue(mergeResult.merged)
        assertEquals("PATCH", stateRequest.method)
        assertEquals("{\"state\":\"closed\"}", stateRequest.body.readUtf8())
    }

    @Test
    fun contentUpdateUsesAuthenticatedPatchAndInvalidatesCache() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                PULL_REQUEST_JSON
                    .replace("Native pull request workflow", "Polished pull request")
                    .replace("## Summary\\nAdds native PR support", "Updated body")
            )
        )
        val cacheStore = TestGithubCacheStore().apply {
            write(
                "pull",
                GithubCachedResponse(
                    body = PULL_REQUEST_JSON,
                    linkHeader = null,
                    etag = "etag",
                    savedAtEpochMillis = 1L
                )
            )
        }
        val repository = repository(cacheStore = cacheStore)

        val updated = repository.updateContent(
            "openai",
            "codex",
            7,
            GithubPullRequestDraft.fromInput("Polished pull request", "Updated body").getOrThrow()
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("/repos/openai/codex/pulls/7", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("{\"title\":\"Polished pull request\",\"body\":\"Updated body\"}", request.body.readUtf8())
        assertEquals("Polished pull request", updated.title)
        assertEquals("Updated body", updated.body)
        assertTrue(cacheStore.isEmpty())
    }

    @Test
    fun rebaseMergeOmitsCustomCommitText() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(MERGE_JSON))
        val repository = repository()

        repository.merge(
            "openai",
            "codex",
            7,
            GithubMergeDraft.fromInput(
                method = GithubMergeMethod.REBASE,
                expectedHeadSha = "head-sha",
                commitTitle = "Ignored title",
                commitMessage = "Ignored message"
            ).getOrThrow()
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("{\"sha\":\"head-sha\",\"merge_method\":\"rebase\"}", request.body.readUtf8())
    }

    @Test
    fun requestedReviewerUpdateRemovesThenAddsUsersAndInvalidatesCache() = runTest {
        val reviewerRemovedJson = PULL_REQUEST_JSON.replace(
            "\"requested_reviewers\": [{ \"login\": \"reviewer\", \"avatar_url\": null, \"html_url\": \"https://github.com/reviewer\" }]",
            "\"requested_reviewers\": []"
        )
        val reviewerAddedJson = reviewerRemovedJson.replace(
            "\"requested_reviewers\": []",
            "\"requested_reviewers\": [{ \"login\": \"bob\", \"avatar_url\": null, \"html_url\": \"https://github.com/bob\" }]"
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(reviewerRemovedJson))
        server.enqueue(MockResponse().setResponseCode(201).setBody(reviewerAddedJson))
        val cacheStore = TestGithubCacheStore().apply {
            write(
                "pull",
                GithubCachedResponse(
                    body = PULL_REQUEST_JSON,
                    linkHeader = null,
                    etag = "etag",
                    savedAtEpochMillis = 1L
                )
            )
        }
        val repository = repository(cacheStore = cacheStore)

        val updated = repository.updateRequestedReviewers(
            "openai",
            "codex",
            7,
            GithubRequestedReviewersUpdate.fromInput(
                current = listOf("reviewer"),
                requested = listOf("bob")
            ).getOrThrow()
        ).getOrThrow()
        val removeRequest = server.takeRequest()
        val addRequest = server.takeRequest()

        assertEquals("DELETE", removeRequest.method)
        assertEquals("/repos/openai/codex/pulls/7/requested_reviewers", removeRequest.path)
        assertEquals("{\"reviewers\":[\"reviewer\"]}", removeRequest.body.readUtf8())
        assertEquals("POST", addRequest.method)
        assertEquals("{\"reviewers\":[\"bob\"]}", addRequest.body.readUtf8())
        assertEquals("Bearer test_token_12345678901234567890", addRequest.getHeader("Authorization"))
        assertEquals(listOf("bob"), updated.requestedReviewers.map { it.login })
        assertTrue(cacheStore.isEmpty())
    }

    @Test
    fun pullRequestDetailUsesEtagAndSuccessfulWriteInvalidatesCachedReads() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"pull-v1\"")
                .setBody(PULL_REQUEST_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                PULL_REQUEST_JSON.replace("\"state\": \"open\"", "\"state\": \"closed\"")
            )
        )
        val cacheStore = TestGithubCacheStore()
        val repository = repository(cacheStore = cacheStore)

        repository.pullRequest("openai", "codex", 7).getOrThrow()
        server.takeRequest()
        repository.pullRequest("openai", "codex", 7).getOrThrow()
        val validationRequest = server.takeRequest()

        assertEquals("\"pull-v1\"", validationRequest.getHeader("If-None-Match"))
        assertFalse(cacheStore.isEmpty())

        repository.updateState("openai", "codex", 7, GithubPullRequestState.CLOSED).getOrThrow()

        assertTrue(cacheStore.isEmpty())
    }

    private fun repository(cacheStore: GithubCacheStore = NoOpGithubCacheStore) = GithubPullRequestsRepositoryImpl(
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
        val PULL_REQUEST_JSON = """
            {
              "id": 700,
              "number": 7,
              "title": "Native pull request workflow",
              "body": "## Summary\nAdds native PR support",
              "state": "open",
              "draft": true,
              "merged": false,
              "locked": true,
              "milestone": {
                "number": 5,
                "title": "Native client",
                "description": null,
                "open_issues": 2,
                "closed_issues": 1,
                "due_on": null
              },
              "mergeable": true,
              "mergeable_state": "clean",
              "comments": 2,
              "review_comments": 1,
              "commits": 3,
              "additions": 12,
              "deletions": 3,
              "changed_files": 2,
              "created_at": "2026-08-15T00:00:00Z",
              "updated_at": "2026-08-16T00:00:00Z",
              "closed_at": null,
              "merged_at": null,
              "html_url": "https://github.com/openai/codex/pull/7",
              "user": { "login": "alice", "avatar_url": null, "html_url": "https://github.com/alice" },
              "labels": [{ "name": "enhancement", "color": "a2eeef", "description": null }],
              "assignees": [],
              "requested_reviewers": [{ "login": "reviewer", "avatar_url": null, "html_url": "https://github.com/reviewer" }],
              "head": { "label": "alice:feature/native-pr", "ref": "feature/native-pr", "sha": "head-sha", "repo": { "full_name": "alice/codex" } },
              "base": { "label": "openai:main", "ref": "main", "sha": "base-sha", "repo": { "full_name": "openai/codex" } }
            }
        """.trimIndent()

        val FILES_JSON = """
            [
              {
                "sha": "file-sha",
                "filename": "app/Main.kt",
                "status": "modified",
                "additions": 1,
                "deletions": 1,
                "changes": 2,
                "blob_url": "https://github.com/openai/codex/blob/head-sha/app/Main.kt",
                "raw_url": "https://raw.githubusercontent.com/openai/codex/head-sha/app/Main.kt",
                "patch": "@@ -1 +1 @@\n-old\n+new"
              }
            ]
        """.trimIndent()

        val REVIEWS_JSON = """
            [
              {
                "id": 801,
                "body": "Ship it",
                "state": "APPROVED",
                "submitted_at": "2026-08-16T01:00:00Z",
                "html_url": "https://github.com/openai/codex/pull/7#pullrequestreview-801",
                "user": { "login": "reviewer", "avatar_url": null, "html_url": "https://github.com/reviewer" }
              }
            ]
        """.trimIndent()

        val REVIEW_COMMENTS_JSON = """
            [
              {
                "id": 901,
                "body": "Please keep this branch explicit.",
                "path": "app/Main.kt",
                "line": 42,
                "start_line": 40,
                "side": "RIGHT",
                "diff_hunk": "@@ -40 +40 @@",
                "created_at": "2026-08-16T01:30:00Z",
                "updated_at": "2026-08-16T01:30:00Z",
                "html_url": "https://github.com/openai/codex/pull/7#discussion_r901",
                "user": { "login": "alice", "avatar_url": null, "html_url": "https://github.com/alice" }
              }
            ]
        """.trimIndent()

        val MERGE_JSON = """
            { "sha": "merge-sha", "merged": true, "message": "Pull Request successfully merged" }
        """.trimIndent()
    }
}
