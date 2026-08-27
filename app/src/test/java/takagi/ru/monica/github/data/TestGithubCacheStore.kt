package takagi.ru.monica.github.data

class TestGithubCacheStore : GithubCacheStore {
    private val values = mutableMapOf<String, GithubCachedResponse>()

    override fun read(key: String): GithubCachedResponse? = values[key]

    override fun write(key: String, response: GithubCachedResponse) {
        values[key] = response
    }

    override fun clear() = values.clear()

    fun isEmpty(): Boolean = values.isEmpty()
}
