package takagi.ru.monica.github.data

import takagi.ru.monica.github.domain.*

/** Reads templates from the default branch, including an owner's shared .github repository. */
class GithubPullRequestTemplates(private val contents: GithubRepositoryContentsRepository) {
    suspend fun load(owner: String, name: String): Result<List<GithubPullRequestTemplate>> = githubRunCatching {
        val local = loadRepository(owner, name)
        if (local.isNotEmpty() || name == ".github") local else loadRepository(owner, ".github")
    }

    private suspend fun loadRepository(owner: String, name: String): List<GithubPullRequestTemplate> {
        val candidates = linkedSetOf<String>()
        for (directory in listOf(".github", "", "docs")) {
            val entries = contents.directory(owner, name, directory).missingAsEmpty()
            val prefix = if (directory.isEmpty()) "" else "$directory/"
            for (entry in entries.filter { it.path == prefix + it.name }) {
                if (entry.type == GithubContentType.FILE && entry.name.lowercase() in setOf("pull_request_template.md", "pull_request_template.markdown")) {
                    candidates += entry.path
                }
                if (entry.type == GithubContentType.DIRECTORY && entry.name.equals("PULL_REQUEST_TEMPLATE", true)) {
                    contents.directory(owner, name, entry.path).missingAsEmpty().filter {
                        it.type == GithubContentType.FILE && it.path == "${entry.path}/${it.name}" &&
                            it.name.substringAfterLast('.').lowercase() in setOf("md", "markdown")
                    }.sortedBy { it.name }.forEach { candidates += it.path }
                }
            }
        }
        return candidates.mapIndexed { index, path ->
            // Keep oversized/excess templates visible, without loading unbounded contents.
            val text = if (index >= 32) null else (contents.file(owner, name, path).getOrThrow() as? GithubFileContent.Text)?.value
            GithubPullRequestTemplate("$owner/$name", path, text?.takeIf { it.length <= 65536 })
        }
    }

    private fun Result<List<GithubContentItem>>.missingAsEmpty(): List<GithubContentItem> = getOrElse {
        if (it is GithubApiException && it.statusCode == 404) emptyList() else throw it
    }
}
