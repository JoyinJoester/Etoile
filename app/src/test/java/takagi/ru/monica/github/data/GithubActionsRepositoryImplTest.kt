package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubActionsConclusion
import takagi.ru.monica.github.domain.GithubActionsStatus
import takagi.ru.monica.github.domain.GithubArtifactOutput
import takagi.ru.monica.github.domain.GithubWorkflowRunAction
import takagi.ru.monica.github.domain.GithubWorkflowState
import java.io.ByteArrayOutputStream

class GithubActionsRepositoryImplTest {
    private lateinit var apiServer: MockWebServer
    private lateinit var downloadServer: MockWebServer

    @Before
    fun setUp() {
        apiServer = MockWebServer().also(MockWebServer::start)
        downloadServer = MockWebServer().also(MockWebServer::start)
    }

    @After
    fun tearDown() {
        apiServer.shutdown()
        downloadServer.shutdown()
    }

    @Test
    fun workflowsAndRunsUsePaginationAndTypedStatusMapping() = runTest {
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${apiServer.url("/repos/openai/codex/actions/workflows?page=2")}>; rel=\"next\"")
                .setBody(WORKFLOWS_JSON)
        )
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody(RUNS_JSON))
        val repository = repository()

        val workflows = repository.workflows("openai", "codex", page = 1, perPage = 30).getOrThrow()
        val workflowsRequest = apiServer.takeRequest()
        val runs = repository.workflowRuns("openai", "codex", workflowId = 11, page = 1, perPage = 30)
            .getOrThrow()
        val runsRequest = apiServer.takeRequest()

        assertEquals("/repos/openai/codex/actions/workflows?per_page=30&page=1", workflowsRequest.path)
        assertEquals(2, workflows.nextPage)
        assertEquals(GithubWorkflowState.ACTIVE, workflows.items.single().state)
        assertEquals(
            "/repos/openai/codex/actions/workflows/11/runs?per_page=30&page=1",
            runsRequest.path
        )
        assertEquals(GithubActionsStatus.COMPLETED, runs.items.single().status)
        assertEquals(GithubActionsConclusion.SUCCESS, runs.items.single().conclusion)
        assertEquals("main", runs.items.single().headBranch)
    }

    @Test
    fun workflowRunActionsUseAuthenticatedMutationEndpoints() = runTest {
        apiServer.enqueue(MockResponse().setResponseCode(201))
        apiServer.enqueue(MockResponse().setResponseCode(202))
        val repository = repository()

        repository.performRunAction("openai", "codex", 44, GithubWorkflowRunAction.RERUN).getOrThrow()
        val rerun = apiServer.takeRequest()
        repository.performRunAction("openai", "codex", 44, GithubWorkflowRunAction.CANCEL).getOrThrow()
        val cancel = apiServer.takeRequest()

        assertEquals("POST", rerun.method)
        assertEquals("/repos/openai/codex/actions/runs/44/rerun", rerun.path)
        assertEquals("POST", cancel.method)
        assertEquals("/repos/openai/codex/actions/runs/44/cancel", cancel.path)
        assertEquals("Bearer test_token_12345678901234567890", rerun.getHeader("Authorization"))
    }

    @Test
    fun workflowDispatchUsesDispatchesEndpointAndKeepsInputValues() = runTest {
        apiServer.enqueue(MockResponse().setResponseCode(204))
        val repository = repository()

        repository.dispatchWorkflow(
            "openai",
            "codex",
            workflowId = 11,
            ref = " main ",
            inputs = mapOf("platform" to "android ", "target" to "= v2")
        ).getOrThrow()
        val request = apiServer.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/actions/workflows/11/dispatches", request.path)
        assertEquals(
            "{\"ref\":\"main\",\"inputs\":{\"platform\":\"android \",\"target\":\"= v2\"}}",
            request.body.readUtf8()
        )
    }

    @Test
    fun workflowDispatchWithoutInputsOmitsInputsObject() = runTest {
        apiServer.enqueue(MockResponse().setResponseCode(204))

        repository().dispatchWorkflow("openai", "codex", workflowId = 11, ref = "main", inputs = emptyMap())
            .getOrThrow()
        val request = apiServer.takeRequest()

        assertEquals("/repos/openai/codex/actions/workflows/11/dispatches", request.path)
        assertEquals("{\"ref\":\"main\"}", request.body.readUtf8())
    }

    @Test
    fun workflowDispatchRejectsBlankNameAndTooManyInputsBeforeRequesting() = runTest {
        val repository = repository()

        val blankName = repository.dispatchWorkflow(
            "openai", "codex", workflowId = 11, ref = "main", inputs = mapOf(" " to "android")
        )
        val tooMany = repository.dispatchWorkflow(
            "openai", "codex", workflowId = 11, ref = "main",
            inputs = (1..26).associate { "input$it" to "v" }
        )

        assertTrue(blankName.isFailure)
        assertTrue(tooMany.isFailure)
        assertEquals(0, apiServer.requestCount)
    }

    @Test
    fun runJobsAndJobDetailsMapStepsWithoutLeakingDtos() = runTest {
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody(RUN_JSON))
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody(JOBS_JSON))
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody(JOB_JSON))
        val repository = repository()

        val run = repository.workflowRun("openai", "codex", runId = 501).getOrThrow()
        apiServer.takeRequest()
        val jobs = repository.jobs("openai", "codex", runId = 501, page = 1, perPage = 100).getOrThrow()
        val jobsRequest = apiServer.takeRequest()
        val job = repository.job("openai", "codex", jobId = 701).getOrThrow()
        val jobRequest = apiServer.takeRequest()

        assertEquals(501L, run.id)
        assertEquals("/repos/openai/codex/actions/runs/501/jobs?filter=latest&per_page=100&page=1", jobsRequest.path)
        assertEquals(GithubActionsConclusion.FAILURE, jobs.items.single().conclusion)
        assertEquals("Run tests", jobs.items.single().steps.last().name)
        assertEquals("/repos/openai/codex/actions/jobs/701", jobRequest.path)
        assertEquals("ubuntu-latest", job.labels.single())
    }

    @Test
    fun jobLogRedirectNeverForwardsAuthorizationAndCapsDownloadedText() = runTest {
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", downloadServer.url("/job.log"))
        )
        downloadServer.enqueue(MockResponse().setResponseCode(200).setBody("0123456789abcdef"))
        val repository = repository(maxLogBytes = 12)

        val log = repository.jobLog("openai", "codex", jobId = 701).getOrThrow()
        val apiRequest = apiServer.takeRequest()
        val downloadRequest = downloadServer.takeRequest()

        assertEquals("Bearer test_token_12345678901234567890", apiRequest.getHeader("Authorization"))
        assertNull(downloadRequest.getHeader("Authorization"))
        assertEquals("0123456789ab", log.text)
        assertTrue(log.isTruncated)
        assertFalse(log.text.contains("test_token"))
    }

    @Test
    fun workflowsUseEtagAndDecodeCachedBodyAfterNotModified() = runTest {
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"workflows-v1\"")
                .setBody(WORKFLOWS_JSON)
        )
        apiServer.enqueue(MockResponse().setResponseCode(304))
        val repository = repository(cacheStore = TestGithubCacheStore())

        repository.workflows("openai", "codex", page = 1, perPage = 30).getOrThrow()
        apiServer.takeRequest()
        val cached = repository.workflows("openai", "codex", page = 1, perPage = 30).getOrThrow()
        val validationRequest = apiServer.takeRequest()

        assertEquals("\"workflows-v1\"", validationRequest.getHeader("If-None-Match"))
        assertEquals("Android CI", cached.items.single().name)
    }

    @Test
    fun artifactsUseTheRunScopedEndpointAndPageForward() = runTest {
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Link",
                    "<${apiServer.url("/repos/openai/codex/actions/runs/99/artifacts?per_page=100&page=2")}>; rel=\"next\""
                )
                .setBody(ARTIFACTS_JSON)
        )
        val repository = repository()

        val artifacts = repository.artifacts("openai", "codex", runId = 99).getOrThrow()
        val request = apiServer.takeRequest()
        val artifact = artifacts.items.single()

        assertEquals("/repos/openai/codex/actions/runs/99/artifacts?per_page=100&page=1", request.path)
        assertEquals(2, artifacts.nextPage)
        assertEquals("android-apk", artifact.name)
        assertEquals(2_097_152L, artifact.sizeBytes)
        assertFalse(artifact.isExpired)
        assertEquals("2026-08-16T00:02:00Z", artifact.createdAt)
    }

    @Test
    fun artifactDownloadStreamsEveryByteAndNeverForwardsAuthorization() = runTest {
        val payload = ByteArray(100_000) { (it % 251).toByte() }
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", downloadServer.url("/artifact.zip"))
        )
        downloadServer.enqueue(
            MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
        )
        val output = RecordingArtifactOutput()
        val repository = repository()

        repository.downloadArtifact("openai", "codex", artifactId = 301) { output }.getOrThrow()
        val apiRequest = apiServer.takeRequest()
        val downloadRequest = downloadServer.takeRequest()

        assertEquals("/repos/openai/codex/actions/artifacts/301/zip", apiRequest.path)
        assertEquals("Bearer test_token_12345678901234567890", apiRequest.getHeader("Authorization"))
        assertEquals("/artifact.zip", downloadRequest.path)
        assertNull(downloadRequest.getHeader("Authorization"))
        assertArrayEquals(payload, output.bytes.toByteArray())
        assertTrue(output.closed)
    }

    @Test
    fun aMissingArtifactFailsBeforeTheDestinationIsOpened() = runTest {
        apiServer.enqueue(MockResponse().setResponseCode(404))
        var openCount = 0
        val repository = repository()

        val result = repository.downloadArtifact("openai", "codex", artifactId = 301) {
            openCount += 1
            RecordingArtifactOutput()
        }

        assertEquals(404, (result.exceptionOrNull() as GithubApiException).statusCode)
        assertEquals(0, openCount)
        assertEquals(0, downloadServer.requestCount)
    }

    private class RecordingArtifactOutput : GithubArtifactOutput {
        val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(source: ByteArray, offset: Int, count: Int) {
            bytes.write(source, offset, count)
        }

        override fun close() {
            closed = true
        }
    }

    private fun repository(
        maxLogBytes: Long = 1024 * 1024,
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubActionsRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()),
        client = OkHttpClient(),
        baseUrl = apiServer.url("/").toString(),
        maxLogBytes = maxLogBytes,
        cacheStore = cacheStore
    )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val WORKFLOWS_JSON = """
            {
              "total_count": 1,
              "workflows": [
                {
                  "id": 11,
                  "name": "Android CI",
                  "path": ".github/workflows/android.yml",
                  "state": "active",
                  "created_at": "2026-08-10T00:00:00Z",
                  "updated_at": "2026-08-16T00:00:00Z",
                  "url": "https://api.github.com/repos/openai/codex/actions/workflows/11",
                  "html_url": "https://github.com/openai/codex/actions/workflows/android.yml",
                  "badge_url": "https://github.com/openai/codex/actions/workflows/android.yml/badge.svg"
                }
              ]
            }
        """.trimIndent()

        val RUN_JSON = """
            {
              "id": 501,
              "workflow_id": 11,
              "name": "Android CI",
              "display_title": "Add native Actions UI",
              "run_number": 42,
              "event": "push",
              "status": "completed",
              "conclusion": "success",
              "head_branch": "main",
              "head_sha": "abc123",
              "html_url": "https://github.com/openai/codex/actions/runs/501",
              "created_at": "2026-08-16T01:00:00Z",
              "updated_at": "2026-08-16T01:10:00Z",
              "run_started_at": "2026-08-16T01:01:00Z",
              "actor": {
                "login": "alice",
                "avatar_url": null,
                "html_url": "https://github.com/alice"
              }
            }
        """.trimIndent()

        val RUNS_JSON = """
            {
              "total_count": 1,
              "workflow_runs": [$RUN_JSON]
            }
        """.trimIndent()

        val JOB_JSON = """
            {
              "id": 701,
              "run_id": 501,
              "name": "build",
              "status": "completed",
              "conclusion": "failure",
              "started_at": "2026-08-16T01:01:00Z",
              "completed_at": "2026-08-16T01:08:00Z",
              "html_url": "https://github.com/openai/codex/actions/runs/501/job/701",
              "runner_name": "GitHub Actions 1",
              "labels": ["ubuntu-latest"],
              "steps": [
                {
                  "name": "Checkout",
                  "status": "completed",
                  "conclusion": "success",
                  "number": 1,
                  "started_at": "2026-08-16T01:01:00Z",
                  "completed_at": "2026-08-16T01:02:00Z"
                },
                {
                  "name": "Run tests",
                  "status": "completed",
                  "conclusion": "failure",
                  "number": 2,
                  "started_at": "2026-08-16T01:02:00Z",
                  "completed_at": "2026-08-16T01:08:00Z"
                }
              ]
            }
        """.trimIndent()

        val JOBS_JSON = """
            {
              "total_count": 1,
              "jobs": [$JOB_JSON]
            }
        """.trimIndent()

        val ARTIFACT_JSON = """
            {
              "id": 301,
              "name": "android-apk",
              "size_in_bytes": 2097152,
              "expired": false,
              "created_at": "2026-08-16T00:02:00Z",
              "archive_download_url": "https://api.github.com/repos/openai/codex/actions/artifacts/301/zip"
            }
        """.trimIndent()

        val ARTIFACTS_JSON = """
            {
              "total_count": 1,
              "artifacts": [$ARTIFACT_JSON]
            }
        """.trimIndent()
    }
}
