package takagi.ru.monica.gitlab.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.gitlab.domain.*

class GitLabWorkRepositoryImplTest {
    @Test fun readsCanonicalConversationWebUrlForMarkdownContext() = runTest {
        withServer { server, repo, key ->
            val url = "https://gitlab.example.com/team/gitlab/group/repo/-/merge_requests/3"
            server.enqueue(ok(conversation().dropLast(1) + ",\"web_url\":\"$url\"}"))
            assertEquals(url, repo.conversation(key, 5, GitLabConversationKind.MERGE_REQUEST, 3).getOrThrow().webUrl)
        }
    }
    private val sha = "a".repeat(40)
    private val kind = GitLabConversationKind.MERGE_REQUEST
    private fun conversation(state: String = "opened", head: String = sha, mergeStatus: String = "mergeable", locked: Boolean = false) =
        """{"id":900,"iid":3,"project_id":5,"title":"Title","description":"Body","state":"$state","author":{"id":8,"username":"alice"},"source_branch":"feature/a","target_branch":"main","sha":"$head","detailed_merge_status":"$mergeStatus","discussion_locked":$locked}"""
    private fun pipeline(status: String = "failed", id: Long = 17) = """{"id":$id,"ref":"main","sha":"$sha","status":"$status"}"""
    private fun job(status: String = "manual", id: Long = 23, pipelineId: Long = 17) =
        """{"id":$id,"name":"deploy","stage":"production","status":"$status","pipeline":{"id":$pipelineId}}"""
    private val note = """{"id":81,"body":"hello","author":{"id":42,"username":"me"},"created_at":"today","system":false}"""

    @Test fun listsUseInstanceSubpathProjectLocalNumbersServerSearchAndPaging() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok("[${conversation()}]").setHeader("X-Next-Page", "2"))
            val page = repo.conversations(key, 5, kind, "opened", "猫 & code", 1).getOrThrow()
            assertEquals(3L, page.items.single().number); assertEquals(900L, page.items.single().id); assertEquals(2, page.nextPage)
            val request = server.takeRequest()
            assertEquals("/team/gitlab/api/v4/projects/5/merge_requests", request.requestUrl!!.encodedPath)
            assertEquals("猫 & code", request.requestUrl!!.queryParameter("search")); assertEquals("all", request.requestUrl!!.queryParameter("scope"))
            assertEquals("glpat-work-token", request.getHeader("PRIVATE-TOKEN"))
            server.enqueue(ok(conversation()))
            repo.conversation(key, 5, kind, 3).getOrThrow()
            assertTrue(server.takeRequest().path!!.endsWith("/merge_requests/3"))
        }
    }

    @Test fun createsIssueAndMergeRequestWithEscapedJsonAndOnlyCreationIncludesBranches() = runTest {
        withServer { server, repo, key ->
            for (type in GitLabConversationKind.entries) {
                server.enqueue(ok(conversation()))
                val draft = GitLabConversationDraft(" A \"title\" ", "Body\n你好", "feature/a", "main")
                repo.create(key, 5, type, draft).getOrThrow()
                val request = server.takeRequest(); val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                assertEquals("POST", request.method); assertEquals("A \"title\"", body["title"]!!.jsonPrimitive.content)
                assertEquals("Body\n你好", body["description"]!!.jsonPrimitive.content)
                assertEquals(type == kind, "source_branch" in body)
            }
            server.enqueue(ok(conversation())); server.enqueue(ok(conversation()))
            repo.edit(key, 5, kind, 3, GitLabConversationDraft("Edited", "Body", "ignored", "ignored")).getOrThrow()
            assertEquals("GET", server.takeRequest().method)
            val edit = server.takeRequest()
            assertEquals("PUT", edit.method)
            assertFalse(edit.body.readUtf8().contains("source_branch"))
        }
    }

    @Test fun invalidDraftsEmptyCommentsAndInvalidActionsNeverSendRequests() = runTest {
        withServer { server, repo, key ->
            assertTrue(repo.create(key, 5, kind, GitLabConversationDraft("", "", "a", "b")).isFailure)
            assertTrue(repo.create(key, 5, kind, GitLabConversationDraft("Title", "", " main ", "main")).isFailure)
            assertTrue(repo.comment(key, 5, kind, 3, " ").isFailure)
            assertTrue(repo.pipelineAction(key, 5, 17, GitLabRunAction.PLAY).isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun quickActionsRequireFreshExplicitApprovalForCommentsAndDescriptions() = runTest {
        withServer { server, repo, key ->
            assertTrue(repo.comment(key, 5, kind, 3, " /close").isFailure)
            val draft = GitLabConversationDraft("Title", "/close", "feature/a", "main")
            assertTrue(repo.create(key, 5, kind, draft).isFailure)
            assertTrue(repo.edit(key, 5, kind, 3, draft).isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(ok(conversation())); server.enqueue(ok(note))
            repo.comment(key, 5, kind, 3, "/close", allowQuickActions = true).getOrThrow()
            server.takeRequest()
            assertEquals("/close", Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["body"]!!.jsonPrimitive.content)
        }
    }

    @Test fun commentsRecheckLockAndUseIssueOrMergeRequestNotesEndpoint() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(conversation(locked = true)))
            assertTrue(repo.comment(key, 5, kind, 3, "hello").isFailure)
            assertEquals(1, server.requestCount); server.takeRequest()
            server.enqueue(ok(conversation())); server.enqueue(ok(note))
            assertEquals(81L, repo.comment(key, 5, GitLabConversationKind.ISSUE, 3, "hello").getOrThrow().id)
            assertTrue(server.takeRequest().path!!.endsWith("/issues/3"))
            assertTrue(server.takeRequest().path!!.endsWith("/issues/3/notes"))
        }
    }

    @Test fun stateChangesReadFreshStateAndSendExplicitStateEvents() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(conversation())); server.enqueue(ok(conversation("closed")))
            assertEquals("closed", repo.changeState(key, 5, kind, 3, true).getOrThrow().state)
            server.takeRequest(); assertEquals("close", Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["state_event"]!!.jsonPrimitive.content)
            server.enqueue(ok(conversation("closed"))); server.enqueue(ok(conversation()))
            repo.changeState(key, 5, kind, 3, false).getOrThrow()
            server.takeRequest(); assertEquals("reopen", Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["state_event"]!!.jsonPrimitive.content)
            server.enqueue(ok(conversation("merged")))
            assertTrue(repo.changeState(key, 5, kind, 3, false).isFailure)
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun mergeBindsConfirmedHeadAndKeepsSourceBranch() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(conversation())); server.enqueue(ok(conversation("merged")))
            val target = GitLabMergeTarget(3, sha, "feature/a", "main")
            assertEquals("merged", repo.merge(key, 5, target).getOrThrow().state)
            assertEquals("GET", server.takeRequest().method)
            val request = server.takeRequest(); val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("PUT", request.method); assertTrue(request.path!!.endsWith("/merge_requests/3/merge"))
            assertEquals(sha, body["sha"]!!.jsonPrimitive.content)
            assertFalse(body["should_remove_source_branch"]!!.jsonPrimitive.boolean)
        }
    }

    @Test fun changedHeadBranchDraftCiAndApprovalBlocksNeverReachMergePut() = runTest {
        withServer { server, repo, key ->
            val target = GitLabMergeTarget(3, sha, "feature/a", "main")
            val invalid = listOf(conversation(head = "b".repeat(40)), conversation(mergeStatus = "ci_must_pass"), conversation(mergeStatus = "not_approved"),
                conversation().replace("\"main\"", "\"release\""), conversation().dropLast(1) + ",\"draft\":true}")
            invalid.forEach { response ->
                server.enqueue(ok(response))
                assertEquals(GitLabReadError.CONFLICT, (repo.merge(key, 5, target).exceptionOrNull() as GitLabReadException).reason)
                assertEquals("GET", server.takeRequest().method)
            }
            assertEquals(invalid.size, server.requestCount)
        }
    }

    @Test fun notesDiffsAndJobsPreservePaginationAndResourceMetadata() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok("[$note]").setHeader("X-Next-Page", "2"))
            assertEquals(2, repo.notes(key, 5, kind, 3).getOrThrow().nextPage)
            assertEquals("desc", server.takeRequest().requestUrl!!.queryParameter("sort"))
            server.enqueue(ok(conversation()))
            server.enqueue(ok("""[{"old_path":"a","new_path":"b","diff":"@@ diff"},{"old_path":"c","new_path":"c","too_large":true}]""").setHeader("X-Next-Page", "2"))
            server.enqueue(ok(conversation()))
            val diffs = repo.diffs(key, 5, 3, sha).getOrThrow()
            assertEquals(2, diffs.nextPage); assertFalse(diffs.items.first().unavailable); assertTrue(diffs.items.last().unavailable)
            assertTrue(server.takeRequest().path!!.endsWith("/merge_requests/3"))
            assertTrue(server.takeRequest().requestUrl!!.encodedPath.endsWith("/merge_requests/3/diffs"))
            assertTrue(server.takeRequest().path!!.endsWith("/merge_requests/3"))
            server.enqueue(ok("[${job()}]").setHeader("X-Next-Page", "2"))
            assertEquals(17L, repo.jobs(key, 5, 17).getOrThrow().items.single().pipelineId)
            assertEquals("false", server.takeRequest().requestUrl!!.queryParameter("include_retried"))
        }
    }

    @Test fun pipelineCreationUsesSingularEndpointAndPipelineListFiltersRef() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok("[${pipeline()}]"))
            repo.pipelines(key, 5, "feature/a").getOrThrow()
            assertEquals("feature/a", server.takeRequest().requestUrl!!.queryParameter("ref"))
            server.enqueue(ok(pipeline("pending")))
            repo.runPipeline(key, 5, "release/v1").getOrThrow()
            val request = server.takeRequest()
            assertEquals("POST", request.method); assertTrue(request.path!!.endsWith("/projects/5/pipeline"))
            assertEquals("release/v1", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["ref"]!!.jsonPrimitive.content)
        }
    }

    @Test fun pipelineRetryAndCancelRecheckCurrentStateAndRejectTerminalCancel() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(pipeline())); server.enqueue(ok(pipeline("pending")))
            repo.pipelineAction(key, 5, 17, GitLabRunAction.RETRY).getOrThrow()
            server.takeRequest(); assertTrue(server.takeRequest().path!!.endsWith("/pipelines/17/retry"))
            server.enqueue(ok(pipeline("running"))); server.enqueue(ok(pipeline("canceled")))
            repo.pipelineAction(key, 5, 17, GitLabRunAction.CANCEL).getOrThrow()
            server.takeRequest(); assertTrue(server.takeRequest().path!!.endsWith("/pipelines/17/cancel"))
            server.enqueue(ok(pipeline("success")))
            assertTrue(repo.pipelineAction(key, 5, 17, GitLabRunAction.CANCEL).isFailure)
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun manualPlayValidatesJobPipelineAndRetryAcceptsNewServerJobId() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(job(pipelineId = 999)))
            assertTrue(repo.jobAction(key, 5, 17, 23, GitLabRunAction.PLAY).isFailure)
            server.takeRequest()
            server.enqueue(ok(job())); server.enqueue(ok(job("pending")))
            repo.jobAction(key, 5, 17, 23, GitLabRunAction.PLAY).getOrThrow()
            server.takeRequest(); assertTrue(server.takeRequest().path!!.endsWith("/jobs/23/play"))
            server.enqueue(ok(job("failed"))); server.enqueue(ok(job("pending", id = 24)))
            assertEquals(24L, repo.jobAction(key, 5, 17, 23, GitLabRunAction.RETRY).getOrThrow().id)
        }
    }

    @Test fun logsValidateIdentityStripTerminalControlsAndNeverFollowLinks() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(job("running")))
            server.enqueue(ok("\u001B[31mred\u001B[0m\n\u001B]8;;https://evil.example\u0007link\u001B]8;;\u0007\n"))
            assertEquals("red\nlink\n", repo.trace(key, 5, 17, 23).getOrThrow())
            server.takeRequest(); assertEquals("text/plain", server.takeRequest().getHeader("Accept"))
            server.enqueue(ok(job("running"))); server.enqueue(ok("x".repeat(1_048_577)))
            assertEquals(GitLabReadError.LOG_TOO_LARGE, (repo.trace(key, 5, 17, 23).exceptionOrNull() as GitLabReadException).reason)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun writesNeverRetryAfterDisconnectAndMalformedSuccessRemainsAFailure() = runTest {
        withServer { server, repo, key ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertTrue(repo.runPipeline(key, 5, "main").isFailure)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)); assertEquals(1, server.requestCount)
            server.enqueue(ok("{broken"))
            assertTrue(repo.runPipeline(key, 5, "main").isFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun writesRejectRedirectsAndExposeSafePermissionConflictAndValidationErrors() = runTest {
        withServer { server, repo, key ->
            for ((status, expected) in listOf(401 to GitLabReadError.AUTHENTICATION, 403 to GitLabReadError.PERMISSION,
                409 to GitLabReadError.CONFLICT, 422 to GitLabReadError.INVALID_INPUT, 429 to GitLabReadError.RATE_LIMIT, 307 to GitLabReadError.NETWORK)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("glpat-secret").setHeader("Location", server.url("/collect")))
                val error = repo.runPipeline(key, 5, "main").exceptionOrNull() as GitLabReadException
                assertEquals(expected, error.reason); assertFalse(error.toString().contains("secret"))
            }
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun retryAfterZeroDoesNotReplayAPost() = runTest {
        withServer { server, repo, key ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(ok(pipeline("pending")))
            assertTrue(repo.runPipeline(key, 5, "main").isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("POST", server.takeRequest().method)
        }
    }

    @Test fun diffRejectsChangedHeadBeforeFetchingPatches() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(conversation(head = "b".repeat(40))))
            val error = repo.diffs(key, 5, 3, sha).exceptionOrNull() as GitLabReadException
            assertEquals(GitLabReadError.CONFLICT, error.reason)
            assertEquals(1, server.requestCount)
            assertTrue(server.takeRequest().path!!.endsWith("/merge_requests/3"))
        }
    }

    @Test fun diffRejectsPatchesWhenHeadChangesDuringRead() = runTest {
        withServer { server, repo, key ->
            server.enqueue(ok(conversation()))
            server.enqueue(ok("""[{"old_path":"a","new_path":"a","diff":"@@ patch"}]"""))
            server.enqueue(ok(conversation(head = "b".repeat(40))))
            val error = repo.diffs(key, 5, 3, sha).exceptionOrNull() as GitLabReadException
            assertEquals(GitLabReadError.CONFLICT, error.reason)
            assertEquals(3, server.requestCount)
        }
    }

    private suspend fun withServer(block: suspend (MockWebServer, GitLabWorkRepositoryImpl, String) -> Unit) {
        MockWebServer().use { server ->
            val credential = GitLabCredential(42, server.url("/team/gitlab/").toString(), "me", "Me", "", "glpat-work-token")
            val repo = GitLabWorkRepositoryImpl(OkHttpClient(), GitLabCredentialProvider { credential }, { it.toHttpUrl() })
            block(server, repo, credential.account().key)
        }
    }
    private fun ok(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")
}
