package takagi.ru.monica.github.domain

/** Loads an already validated GitHub avatar URL without coupling UI to HTTP or storage. */
interface GithubAvatarRepository {
    suspend fun bytes(url: String): Result<ByteArray?>
}
