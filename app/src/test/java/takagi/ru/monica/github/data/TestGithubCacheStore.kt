package takagi.ru.monica.github.data

class TestGithubCacheStore : GithubCacheStore {
    // Repository tests opt into validator requests by default; freshness is covered explicitly in GithubCacheTest.
    override val defaultMaxAgeMillis: Long = 0L

    private val values = mutableMapOf<String, GithubCachedResponse>()

    override fun read(key: String): GithubCachedResponse? = values[key]

    override fun write(key: String, response: GithubCachedResponse) {
        values[key] = response
    }

    override fun clear() = values.clear()

    fun isEmpty(): Boolean = values.isEmpty()
}
