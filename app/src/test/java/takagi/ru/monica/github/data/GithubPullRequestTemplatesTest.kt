package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*

class GithubPullRequestTemplatesTest {
    @Test fun findsCaseInsensitiveDefaultAndMultipleTemplatesWithoutSharedFallback() = runTest {
        val contents = FakeContents()
        contents.directories["repo:.github"] = listOf(entry(".github/pull_request_template.md"), entry(".github/PULL_REQUEST_TEMPLATE", true))
        contents.directories["repo:.github/PULL_REQUEST_TEMPLATE"] = listOf(entry(".github/PULL_REQUEST_TEMPLATE/fix.md"), entry(".github/PULL_REQUEST_TEMPLATE/readme.txt"))
        val templates = GithubPullRequestTemplates(contents).load("owner", "repo").getOrThrow()
        assertEquals(2, templates.size)
        assertTrue(templates.all { it.repository == "owner/repo" && it.body == "Template" })
        assertFalse(contents.reads.any { it.startsWith(".github:") })
    }

    @Test fun fallsBackToSharedRepositoryAndKeepsUnsupportedTemplateVisible() = runTest {
        val contents = FakeContents()
        contents.directories[".github:docs"] = listOf(entry("docs/PULL_REQUEST_TEMPLATE.md"))
        contents.fileResult = GithubFileContent.TooLarge
        val templates = GithubPullRequestTemplates(contents).load("owner", "repo").getOrThrow()
        assertEquals("owner/.github", templates.single().repository)
        assertNull(templates.single().body)
    }

    @Test fun permissionFailureDoesNotBecomeAnEmptyCatalog() = runTest {
        val contents = FakeContents().apply { forbidden = true }
        assertTrue(GithubPullRequestTemplates(contents).load("owner", "repo").isFailure)
        assertEquals(listOf("repo:.github"), contents.reads)
    }

    private fun entry(path: String, directory: Boolean = false) = GithubContentItem(path.substringAfterLast('/'), path, "sha", 10,
        if (directory) GithubContentType.DIRECTORY else GithubContentType.FILE, null, null)

    private class FakeContents : GithubRepositoryContentsRepository {
        val directories = mutableMapOf<String, List<GithubContentItem>>()
        val reads = mutableListOf<String>()
        var fileResult: GithubFileContent = GithubFileContent.Text("Template")
        var forbidden = false
        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubBranch>> = error("unused")
        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubTag>> = error("unused")
        override suspend fun directory(owner: String, name: String, path: String, ref: String?): Result<List<GithubContentItem>> {
            reads += "$name:$path"
            return if (forbidden) Result.failure(GithubApiException(403))
            else directories["$name:$path"]?.let { Result.success(it) } ?: Result.failure(GithubApiException(404))
        }
        override suspend fun file(owner: String, name: String, path: String, ref: String?) = Result.success(fileResult)
    }
}
