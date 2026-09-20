package takagi.ru.monica.github.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubStarLabel
import takagi.ru.monica.github.domain.GithubStarLabelError
import takagi.ru.monica.github.domain.GithubStarLabelException
import takagi.ru.monica.github.domain.GithubStarLabelStore
import takagi.ru.monica.github.domain.GithubStarsRepository
import takagi.ru.monica.github.domain.MAX_STAR_LABELS
import takagi.ru.monica.github.domain.validateStarLabelName

class GithubStarsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubStarsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun starredRepositories(
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepository>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("user")
                .addPathSegment("starred")
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "desc")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("starred", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.builder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubRepositoryDto.serializer()),
                        body
                    ).map(GithubRepositoryDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }
}

@Serializable
private data class StarLabelsSnapshot(
    val nextLabelId: Long = 1,
    val labels: List<StoredStarLabel> = emptyList(),
    val assignments: Map<String, List<Long>> = emptyMap()
)

@Serializable
private data class StoredStarLabel(val id: Long, val name: String)

/**
 * Persists user-defined star labels and their assignments. The whole graph is
 * small (tens of labels, hundreds of repositories) so it is stored as a single
 * JSON document rather than one key per repository.
 */
class GithubPreferencesStarLabelStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : GithubStarLabelStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("etoile_github_star_categories", Context.MODE_PRIVATE)

    override fun labels(): List<GithubStarLabel> = read().labels.map { it.toDomain() }

    override fun labelsFor(repositoryId: Long): List<GithubStarLabel> {
        val snapshot = read()
        val assigned = snapshot.assignments[repositoryId.toString()].orEmpty().toSet()
        return snapshot.labels.filter { it.id in assigned }.map { it.toDomain() }
    }

    override fun createLabel(name: String): Result<GithubStarLabel> {
        val snapshot = read()
        if (snapshot.labels.size >= MAX_STAR_LABELS) {
            return Result.failure(GithubStarLabelException(GithubStarLabelError.LIMIT_REACHED))
        }
        val validated = validateStarLabelName(name, snapshot.labels.map { it.toDomain() })
            .getOrElse { return Result.failure(it) }
        val created = StoredStarLabel(snapshot.nextLabelId, validated)
        write(
            snapshot.copy(
                nextLabelId = snapshot.nextLabelId + 1,
                labels = snapshot.labels + created
            )
        )
        return Result.success(created.toDomain())
    }

    override fun renameLabel(labelId: Long, name: String): Result<GithubStarLabel> {
        val snapshot = read()
        if (snapshot.labels.none { it.id == labelId }) {
            return Result.failure(GithubStarLabelException(GithubStarLabelError.BLANK_NAME))
        }
        val validated = validateStarLabelName(name, snapshot.labels.map { it.toDomain() }, labelId)
            .getOrElse { return Result.failure(it) }
        val renamed = StoredStarLabel(labelId, validated)
        write(snapshot.copy(labels = snapshot.labels.map { if (it.id == labelId) renamed else it }))
        return Result.success(renamed.toDomain())
    }

    override fun deleteLabel(labelId: Long) {
        val snapshot = read()
        write(
            snapshot.copy(
                labels = snapshot.labels.filterNot { it.id == labelId },
                assignments = snapshot.assignments
                    .mapValues { (_, ids) -> ids.filterNot { it == labelId } }
                    .filterValues { it.isNotEmpty() }
            )
        )
    }

    override fun toggleAssignment(repositoryId: Long, labelId: Long): List<GithubStarLabel> {
        val snapshot = read()
        if (snapshot.labels.none { it.id == labelId }) return labelsFor(repositoryId)
        val key = repositoryId.toString()
        val current = snapshot.assignments[key].orEmpty()
        val updated = if (labelId in current) current - labelId else current + labelId
        val assignments = if (updated.isEmpty()) {
            snapshot.assignments - key
        } else {
            snapshot.assignments + (key to updated)
        }
        write(snapshot.copy(assignments = assignments))
        return snapshot.labels.filter { it.id in updated.toSet() }.map { it.toDomain() }
    }

    override fun assignLabel(
        repositoryIds: Set<Long>,
        labelId: Long,
        assigned: Boolean
    ): Map<Long, List<GithubStarLabel>> {
        val snapshot = read()
        if (repositoryIds.isEmpty() || snapshot.labels.none { it.id == labelId }) {
            return repositoryIds.associateWith { labelsFor(it) }
        }
        val assignments = snapshot.assignments.toMutableMap()
        val result = mutableMapOf<Long, List<GithubStarLabel>>()
        repositoryIds.forEach { repositoryId ->
            val key = repositoryId.toString()
            val current = assignments[key].orEmpty()
            val updated = when {
                assigned && labelId !in current -> current + labelId
                !assigned -> current - labelId
                else -> current
            }
            if (updated.isEmpty()) assignments -= key else assignments[key] = updated
            result[repositoryId] = snapshot.labels
                .filter { it.id in updated.toSet() }
                .map { it.toDomain() }
        }
        write(snapshot.copy(assignments = assignments))
        return result
    }

    private fun read(): StarLabelsSnapshot {
        preferences.getString(SNAPSHOT_KEY, null)?.let { stored ->
            return runCatching { json.decodeFromString(StarLabelsSnapshot.serializer(), stored) }
                .getOrDefault(StarLabelsSnapshot())
        }
        return migrateLegacyCategories().also(::write)
    }

    /**
     * Earlier builds stored one fixed category name per repository under a bare
     * repository-id key. Those groupings are rebuilt as ordinary labels so the
     * upgrade does not silently drop the user's organization.
     */
    private fun migrateLegacyCategories(): StarLabelsSnapshot {
        val legacy = preferences.all
            .mapNotNull { (key, value) ->
                val repositoryId = key.toLongOrNull() ?: return@mapNotNull null
                val category = (value as? String)?.takeUnless { it in LEGACY_IGNORED_CATEGORIES }
                    ?: return@mapNotNull null
                repositoryId to category
            }
        if (legacy.isEmpty()) return StarLabelsSnapshot()

        val names = legacy.map { it.second }.distinct()
        val labels = names.mapIndexed { index, name ->
            StoredStarLabel(index + 1L, LEGACY_LABEL_NAMES[name] ?: name)
        }
        val labelIdByCategory = names.withIndex().associate { (index, name) -> name to index + 1L }
        return StarLabelsSnapshot(
            nextLabelId = labels.size + 1L,
            labels = labels,
            assignments = legacy.groupBy({ it.first.toString() }) { labelIdByCategory.getValue(it.second) }
        )
    }

    private fun write(snapshot: StarLabelsSnapshot) {
        preferences.edit()
            .clear()
            .putString(SNAPSHOT_KEY, json.encodeToString(StarLabelsSnapshot.serializer(), snapshot))
            .apply()
    }

    private fun StoredStarLabel.toDomain() = GithubStarLabel(id, name)

    private companion object {
        const val SNAPSHOT_KEY = "labels_v2"
        val LEGACY_IGNORED_CATEGORIES = setOf("ALL", "UNCATEGORIZED")
        val LEGACY_LABEL_NAMES = mapOf(
            "ANDROID" to "Android",
            "KOTLIN" to "Kotlin",
            "TOOLS" to "Tools"
        )
    }
}
