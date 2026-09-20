package takagi.ru.monica.github.domain

class StoreReleaseResolver(private val repository: GithubReleasesRepository, private val supportedAbis: List<String> = emptyList()) {
    suspend fun resolve(owner: String, name: String): GithubRelease? {
        var page = 1
        val visited = mutableSetOf<Int>()
        while (visited.add(page)) {
            val result = repository.releases(owner, name, page, 30).getOrThrow()
            result.items.filterNot { it.isDraft }.forEach { release ->
                val apks = release.assets.filter { asset ->
                    val abi = GithubApkParser.parse(asset).abi
                    asset.name.endsWith(".apk", true) &&
                        (supportedAbis.isEmpty() || abi == null || abi == "universal" || abi in supportedAbis)
                }
                if (apks.isNotEmpty()) return release.copy(assets = apks)
            }
            page = result.nextPage ?: return null
        }
        return null
    }
}
