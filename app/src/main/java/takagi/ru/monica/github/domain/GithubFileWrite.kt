package takagi.ru.monica.github.domain

class GithubFileWrite private constructor(
    val path: String, val branch: String, val message: String,
    val content: String?, val expectedSha: String?
) {
    companion object {
        fun fromInput(path: String, branch: String, message: String, content: String?, expectedSha: String?): Result<GithubFileWrite> = runCatching {
            require(path.isNotEmpty() && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
            require(path.none { it == '\\' || it.code < 32 || it.code == 127 })
            require(branch.isNotBlank() && branch.none { it.isWhitespace() || it.code < 32 })
            require(message.isNotBlank() && message.length <= 65536)
            require(content == null || content.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
            require(expectedSha == null || expectedSha.matches(Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")))
            require(content != null || expectedSha != null) // Deletion always targets a known blob.
            GithubFileWrite(path, branch, message.trim(), content, expectedSha)
        }
    }
}

data class GithubFileWriteResult(val commitSha: String, val contentSha: String?)
