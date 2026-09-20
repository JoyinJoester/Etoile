package takagi.ru.monica.github.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubIssueTemplate
import takagi.ru.monica.github.domain.GithubIssueTemplateCatalog
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository

class GithubIssueTemplatesRepositoryImpl(
    private val contents: GithubRepositoryContentsRepository
) : GithubIssueTemplatesRepository {
    override suspend fun templates(owner: String, name: String): Result<GithubIssueTemplateCatalog> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                loadRepository(owner, name)
                    ?: if (name != ".github") loadRepository(owner, ".github") ?: GithubIssueTemplateCatalog()
                    else GithubIssueTemplateCatalog()
            }
        }

    private suspend fun loadRepository(owner: String, name: String): GithubIssueTemplateCatalog? {
        val entries = contents.directory(owner, name, TEMPLATE_DIRECTORY).missingAsNull()
        if (!entries.isNullOrEmpty()) {
            val files = entries.filter {
                it.type == GithubContentType.FILE && it.path == "$TEMPLATE_DIRECTORY/${it.name}"
            }.sortedBy { it.name }
            val configFile = files.firstOrNull { it.name.equals("config.yml", ignoreCase = true) }
                ?: files.firstOrNull { it.name.equals("config.yaml", ignoreCase = true) }
            val config = if (configFile != null) {
                // A broken config must not accidentally enable blank issues.
                GithubIssueTemplateParser.config(readText(owner, name, configFile.path))
            } else GithubIssueTemplateCatalog()
            val candidates = files.filter {
                it.name.lowercase() !in setOf("config.yml", "config.yaml") &&
                    it.name.substringAfterLast('.').lowercase() in setOf("md", "markdown", "yml", "yaml")
            }
            val permits = Semaphore(4)
            val templates = coroutineScope {
                candidates.mapIndexed { index, file ->
                    async {
                        permits.withPermit {
                            if (index >= 64 || file.size > GithubIssueTemplateParser.MAX_TEMPLATE_BYTES) unavailable(file.path)
                            else parseFile(owner, name, file.path)
                        }
                    }
                }.awaitAll()
            }
            return config.copy(templates = templates)
        }
        for (path in LEGACY_PATHS) {
            val file = contents.file(owner, name, path).missingAsNull() ?: continue
            val template = try {
                GithubIssueTemplateParser.parse(path, file.text(), legacy = true)
            } catch (_: Exception) {
                unavailable(path)
            }
            return GithubIssueTemplateCatalog(templates = listOf(template))
        }
        return null
    }

    private suspend fun parseFile(owner: String, name: String, path: String): GithubIssueTemplate = try {
        GithubIssueTemplateParser.parse(path, readText(owner, name, path))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        unavailable(path)
    }

    private fun unavailable(path: String) = GithubIssueTemplate(
        id = path, name = path.substringAfterLast('/'), isSupported = false
    )

    private suspend fun readText(owner: String, name: String, path: String): String =
        contents.file(owner, name, path).getOrThrow().text()

    private fun GithubFileContent.text(): String =
        (this as? GithubFileContent.Text)?.value ?: error("Template is not readable text")

    private fun <T> Result<T>.missingAsNull(): T? = getOrElse {
        if (it is GithubApiException && it.statusCode == 404) null else throw it
    }

    private companion object {
        const val TEMPLATE_DIRECTORY = ".github/ISSUE_TEMPLATE"
        val LEGACY_PATHS = listOf(".github/ISSUE_TEMPLATE.md", "ISSUE_TEMPLATE.md", "docs/ISSUE_TEMPLATE.md")
    }
}
