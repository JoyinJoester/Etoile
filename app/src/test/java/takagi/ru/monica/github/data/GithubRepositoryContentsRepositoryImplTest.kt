package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import takagi.ru.monica.github.domain.GithubFileWrite
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubFileContent

class GithubRepositoryContentsRepositoryImplTest {
    @Test fun writesUtf8ContentWithExplicitBranchAndExpectedBlob() = runTest {
        val sha = "a".repeat(40)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"commit":{"sha":"commit"},"content":{"sha":"newblob"}}"""))
        val change = GithubFileWrite.fromInput("docs/说明.md", "feature/mobile", "Update docs", "你好\n", sha).getOrThrow()
        val result = repository(token = "test_token_12345678901234567890").write("o", "r", change).getOrThrow()
        assertEquals("commit", result.commitSha)
        assertEquals("newblob", result.contentSha)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(listOf("repos", "o", "r", "contents", "docs", "说明.md"), request.requestUrl!!.pathSegments)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("feature/mobile", body.getValue("branch").jsonPrimitive.content)
        assertEquals(sha, body.getValue("sha").jsonPrimitive.content)
        assertEquals("你好\n", String(java.util.Base64.getDecoder().decode(body.getValue("content").jsonPrimitive.content), Charsets.UTF_8))
    }

    @Test fun createsEmptyFilesAndDeletesOnlyKnownBlobs() = runTest {
        val repo = repository(token = "test_token_12345678901234567890")
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"commit":{"sha":"commit"},"content":{"sha":"blob"}}"""))
        repo.write("o", "r", GithubFileWrite.fromInput("empty.txt", "main", "Create", "", null).getOrThrow()).getOrThrow()
        val create = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(!create.containsKey("sha"))
        assertEquals("", create.getValue("content").jsonPrimitive.content)
        server.enqueue(MockResponse().setBody("""{"commit":{"sha":"deleted"},"content":null}"""))
        val removed = repo.write("o", "r", GithubFileWrite.fromInput("empty.txt", "main", "Delete", null, "a".repeat(40)).getOrThrow()).getOrThrow()
        assertNull(removed.contentSha)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertTrue(!Json.parseToJsonElement(delete.body.readUtf8()).jsonObject.containsKey("content"))
    }

    @Test fun conflictsAreReturnedWithoutRetryingWithNewSha() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        val result = repository(token = "test_token_12345678901234567890").write("o", "r",
            GithubFileWrite.fromInput("file.txt", "main", "Update", "new", "a".repeat(40)).getOrThrow())
        assertTrue(result.exceptionOrNull() is GithubApiException)
        assertEquals(1, server.requestCount)
    }
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
    fun directoryUsesEncodedPathAndMapsContentKinds() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(DIRECTORY_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val items = repository.directory("openai", "codex", "app/src main", "main").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/contents/app/src%20main?ref=main", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(GithubContentType.DIRECTORY, items.first().type)
        assertEquals(GithubContentType.FILE, items.last().type)
        assertEquals("Main.kt", items.last().name)
    }

    @Test
    fun directoryUsesEtagAndDecodesCachedBodyAfterNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"directory-v1\"")
                .setBody(DIRECTORY_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cacheStore = TestGithubCacheStore()
        val repository = repository(token = null, cacheStore = cacheStore)

        repository.directory("openai", "codex", "", "main").getOrThrow()
        server.takeRequest()
        val cached = repository.directory("openai", "codex", "", "main").getOrThrow()
        val validationRequest = server.takeRequest()

        assertEquals("\"directory-v1\"", validationRequest.getHeader("If-None-Match"))
        assertEquals(listOf("feature", "Main.kt"), cached.map { it.name })
    }

    @Test
    fun fileReadsMetadataAndDecodesWrappedBase64WithTheBlobSha() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"name":"Main.kt","sha":"${"c".repeat(40)}","size":93,"encoding":"base64",""" +
                        """"content":"ZnVuIG1haW4oKSB7CiAgICBwcmludGxuKCJoZWxsbyBmcm9tIGEgcmVwb3Np""" +
                        """\ndG9yeSBmaWxlIHRoYXQgaXMgbG9uZyBlbm91Z2ggdG8gYmUgd3JhcHBlZCIp\nCn0K"}"""
                )
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val content = repository.file("openai", "codex", "Main.kt", "main").getOrThrow()
        val request = server.takeRequest()

        assertEquals("application/vnd.github.v3+json", request.getHeader("Accept"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(
            GithubFileContent.Text(
                "fun main() {\n    println(\"hello from a repository file that is long enough to be wrapped\")\n}\n",
                "c".repeat(40)
            ),
            content
        )
    }

    @Test
    fun fileKeepsJsonSourceFilesVerbatimInsteadOfGuessingMetadataFromTheBody() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"name":"payload.json","sha":"${"d".repeat(40)}","size":49,"encoding":"base64",""" +
                        """"content":"eyJjb250ZW50IjoicHJpbnQoJ2hpJykiLCJzaGEiOiJub3QtYS1yZWFsLWJsb2IifQ=="}"""
                )
        )

        val content = repository(token = null).file("openai", "codex", "payload.json", "main").getOrThrow()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(
            "{\"content\":\"print('hi')\",\"sha\":\"not-a-real-blob\"}",
            (content as GithubFileContent.Text).value
        )
    }

    @Test
    fun fileClassifiesBinaryAndOversizedPayloadsFromMetadata() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"image.png","sha":"${"e".repeat(40)}","size":6,"content":"AAECAwQF"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name":"huge.log","sha":"${"f".repeat(40)}","size":900000,"content":null}""")
        )
        val repository = repository(token = null)

        val binary = repository.file("openai", "codex", "image.png", "main").getOrThrow()
        val large = repository.file("openai", "codex", "huge.log", "main").getOrThrow()

        assertTrue(binary is GithubFileContent.Binary)
        assertTrue(large is GithubFileContent.TooLarge)
    }

    @Test
    fun fileParsesLiveContentsApiResponseVerbatim() = runTest {
        // Captured from GET api.github.com/repos/octocat/Hello-World/contents/README on 2026-09-19.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"name":"README","path":"README","sha":"980a0d5f19a64b4b30a87d4206aade58726b60e3","size":13,""" +
                    """"url":"https://api.github.com/repos/octocat/Hello-World/contents/README?ref=master",""" +
                    """"html_url":"https://github.com/octocat/Hello-World/blob/master/README",""" +
                    """"git_url":"https://api.github.com/repos/octocat/Hello-World/git/blobs/980a0d5f19a64b4b30a87d4206aade58726b60e3",""" +
                    """"download_url":"https://raw.githubusercontent.com/octocat/Hello-World/master/README",""" +
                    """"type":"file","content":"SGVsbG8gV29ybGQhCg==\n","encoding":"base64",""" +
                    """"_links":{"self":"https://api.github.com/repos/octocat/Hello-World/contents/README?ref=master"}}"""
            )
        )

        val content = repository(token = null).file("octocat", "Hello-World", "README", "master").getOrThrow()

        assertEquals(
            GithubFileContent.Text("Hello World!\n", "980a0d5f19a64b4b30a87d4206aade58726b60e3"),
            content
        )
    }

    @Test
    fun branchesUseRepositoryEndpointAndMapProtectionStateAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/branches?page=2&per_page=2")}>; rel=\"next\"")
                .setBody(BRANCHES_JSON)
        )
        val repository = repository(token = null)

        val page = repository.branches("openai", "codex", page = 1, perPage = 2).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/branches?per_page=2&page=1", request.path)
        assertEquals(listOf("main", "release"), page.items.map { it.name })
        assertEquals(2, page.nextPage)
        assertTrue(page.items.last().isProtected)
    }

    @Test
    fun createsBranchFromLiveCommitShaAndInvalidatesWithGitRefEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {"ref":"refs/heads/feature/mobile","object":{"sha":"new-sha"}}
        """))
        val branch = repository(token = "test_token_12345678901234567890")
            .createBranch("openai", "codex", "feature/mobile", BASE_SHA).getOrThrow()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/git/refs", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("refs/heads/feature/mobile", body.getValue("ref").jsonPrimitive.content)
        assertEquals(BASE_SHA, body.getValue("sha").jsonPrimitive.content)
        assertEquals("feature/mobile", branch.name)
        assertEquals("new-sha", branch.sha)
    }

    @Test
    fun resolveRefReadsTheCurrentTipFromTheCommitEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"sha":"$BASE_SHA","commit":{"message":"tip"}}"""))

        val sha = repository(token = "test_token_12345678901234567890")
            .resolveRef("openai", "codex", "feature/mobile").getOrThrow()
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        // Live probe: GitHub accepts the encoded slash here and returns the same tip as the raw form.
        assertEquals("/repos/openai/codex/commits/feature%2Fmobile", request.path)
        assertEquals(BASE_SHA, sha)
    }

    @Test
    fun resolveRefReportsMissingBranchesAndRejectsMalformedOnes() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val repository = repository(token = "test_token_12345678901234567890")

        val missing = repository.resolveRef("openai", "codex", "deleted-branch")
        assertTrue(missing.exceptionOrNull() is GithubApiException)
        assertEquals(1, server.requestCount)
        assertTrue(repository.resolveRef("openai", "codex", "bad name").isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsMalformedBranchBeforeNetworkRequest() = runTest {
        val result = repository(token = "test_token_12345678901234567890")
            .createBranch("openai", "codex", "../bad", BASE_SHA)
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rejectsProtectedLikeAndStaleBaseCommitsWithoutRequest() = runTest {
        val repository = repository(token = "test_token_12345678901234567890")

        assertTrue(repository.createBranch("openai", "codex", "feature.lock", BASE_SHA).isFailure)
        assertTrue(repository.createBranch("openai", "codex", "feature/new", "main").isFailure)
        assertTrue(repository.deleteBranch("openai", "codex", "feature//mobile").isFailure)
        assertTrue(repository.renameBranch("openai", "codex", "old", "@{new}").isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun deletesNestedBranchUsingGitRefsEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repository(token = "test_token_12345678901234567890")
            .deleteBranch("openai", "codex", "feature/mobile").getOrThrow()
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/git/refs/heads/feature/mobile", request.path)
    }

    @Test
    fun renamesBranchUsingBranchesEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {"name":"feature/new","commit":{"sha":"renamed-sha"},"protected":false}
        """))
        val branch = repository(token = "test_token_12345678901234567890")
            .renameBranch("openai", "codex", "feature/old", "feature/new").getOrThrow()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/branches/feature%2Fold/rename", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("feature/new", body.getValue("new_name").jsonPrimitive.content)
        assertEquals("renamed-sha", branch.sha)
    }

    @Test
    fun tagsUseNativeEndpointAndMapTagCommit() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TAGS_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.tags("openai", "codex", page = 2, perPage = 50).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/tags?per_page=50&page=2", request.path)
        assertEquals("v1.2.0", page.items.single().name)
        assertEquals("tag-sha", page.items.single().sha)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    @Test
    fun createsLightweightTagThroughGitRefsEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"ref":"refs/tags/v1.2.0","object":{"sha":"tag-sha"}}""")
        )
        val tag = repository(token = "test_token_12345678901234567890")
            .createTag("openai", "codex", "v1.2.0", BASE_SHA).getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/git/refs", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("refs/tags/v1.2.0", body.getValue("ref").jsonPrimitive.content)
        assertEquals(BASE_SHA, body.getValue("sha").jsonPrimitive.content)
        assertEquals("v1.2.0", tag.name)
        assertEquals("tag-sha", tag.sha)
    }

    @Test
    fun deletesNamespacedTagFromItsOwnRefPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repository(token = "test_token_12345678901234567890")
            .deleteTag("openai", "codex", "release/1.2.0").getOrThrow()
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/git/refs/tags/release/1.2.0", request.path)
    }

    @Test
    fun rejectsMalformedTagsAndNonCommitShasWithoutRequest() = runTest {
        val repository = repository(token = "test_token_12345678901234567890")

        assertTrue(repository.createTag("openai", "codex", "..bad", BASE_SHA).isFailure)
        assertTrue(repository.createTag("openai", "codex", "v1.2.0", "refs/heads/main").isFailure)
        assertTrue(repository.deleteTag("openai", "codex", "bad name").isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun tagWritesStayLocalWhenSignedOut() = runTest {
        val repository = repository(token = null)

        assertTrue(repository.createTag("openai", "codex", "v1.2.0", BASE_SHA).isFailure)
        assertTrue(repository.deleteTag("openai", "codex", "v1.2.0").isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun conflictingTagReportsTheStatusCodeWithoutRetrying() = runTest {
        server.enqueue(MockResponse().setResponseCode(422))

        val result = repository(token = "test_token_12345678901234567890")
            .createTag("openai", "codex", "v1.2.0", BASE_SHA)

        assertEquals(422, (result.exceptionOrNull() as GithubApiException).statusCode)
        assertEquals(1, server.requestCount)
    }

    private fun repository(
        token: String?,
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubRepositoryContentsRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore(token)),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private class FakeTokenStore(private val token: String?) : GithubTokenStore {
        override fun read() = token
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val BASE_SHA = "a".repeat(40)

        val DIRECTORY_JSON = """
            [
              {
                "name": "feature",
                "path": "app/src main/feature",
                "sha": "dir-sha",
                "size": 0,
                "type": "dir",
                "html_url": "https://github.com/openai/codex/tree/main/app/src%20main/feature",
                "download_url": null
              },
              {
                "name": "Main.kt",
                "path": "app/src main/Main.kt",
                "sha": "file-sha",
                "size": 120,
                "type": "file",
                "html_url": "https://github.com/openai/codex/blob/main/app/src%20main/Main.kt",
                "download_url": "https://raw.githubusercontent.com/openai/codex/main/app/src%20main/Main.kt"
              }
            ]
        """.trimIndent()

        val BRANCHES_JSON = """
            [
              { "name": "main", "commit": { "sha": "main-sha" }, "protected": false },
              { "name": "release", "commit": { "sha": "release-sha" }, "protected": true }
            ]
        """.trimIndent()

        val TAGS_JSON = """
            [
              { "name": "v1.2.0", "commit": { "sha": "tag-sha" } }
            ]
        """.trimIndent()
    }
}
