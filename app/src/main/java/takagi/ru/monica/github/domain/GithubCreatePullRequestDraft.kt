package takagi.ru.monica.github.domain

/** Validated creation input; head may use GitHub's owner:branch syntax for a fork. */
class GithubCreatePullRequestDraft private constructor(
    val title: String,
    val body: String,
    val base: String,
    val head: String,
    val draft: Boolean,
    val maintainerCanModify: Boolean,
    val headRepository: String?
) {
    companion object {
        fun fromInput(
            title: String, body: String, base: String, head: String,
            draft: Boolean = false, maintainerCanModify: Boolean = false,
            headRepository: String? = null
        ): Result<GithubCreatePullRequestDraft> = runCatching {
            val normalizedTitle = title.trim()
            val target = base.trim()
            val source = head.trim()
            val repository = headRepository?.trim()?.takeIf(String::isNotEmpty)
            require(normalizedTitle.isNotEmpty() && normalizedTitle.length <= 256)
            require(body.length <= 65536)
            require(validBranch(target))
            val parts = source.split(':')
            require(parts.size in 1..2 && validBranch(parts.last()))
            if (parts.size == 2) require(parts.first().matches(Regex("[A-Za-z0-9][A-Za-z0-9-]*")))
            require(source != target)
            require(repository == null || (parts.size == 2 && repository.matches(Regex("[A-Za-z0-9_.-]+"))))
            GithubCreatePullRequestDraft(normalizedTitle, body, target, source, draft, maintainerCanModify, repository)
        }

        private fun validBranch(value: String): Boolean =
            value.isNotBlank() && value != "@" && !value.startsWith('-') &&
                !value.endsWith('.') && !value.contains("..") && !value.contains("@{") &&
                value.none { it.isWhitespace() || it.code < 32 || it.code == 127 || it in "~^:?*[\\" } &&
                value.split('/').all { it.isNotEmpty() && !it.startsWith('.') && !it.endsWith(".lock") }
    }
}
