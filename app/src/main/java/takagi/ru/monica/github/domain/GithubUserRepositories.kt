package takagi.ru.monica.github.domain

interface GithubUserRepositoriesRepository {
    suspend fun repositories(
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRepository>>

    suspend fun create(draft: GithubRepositoryCreateDraft): Result<GithubRepository>
}

/** What the caller wants in a brand new repository, already reduced to what the API accepts. */
class GithubRepositoryCreateDraft private constructor(
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
    val autoInit: Boolean
) {
    companion object {
        const val MAX_NAME_LENGTH = 100

        private val NAME_PATTERN = Regex("^[A-Za-z0-9._-]+$")

        // The names GitHub refuses on its own form, so the screen can say so before a round trip.
        fun isValidName(name: String): Boolean {
            val trimmed = name.trim()
            return trimmed.isNotEmpty() && trimmed.length <= MAX_NAME_LENGTH && NAME_PATTERN.matches(trimmed)
        }

        fun fromInput(
            name: String,
            description: String?,
            isPrivate: Boolean,
            autoInit: Boolean
        ): Result<GithubRepositoryCreateDraft> = runCatching {
            require(isValidName(name))
            GithubRepositoryCreateDraft(
                name = name.trim(),
                description = description?.trim()?.takeIf(String::isNotEmpty),
                isPrivate = isPrivate,
                autoInit = autoInit
            )
        }
    }
}
