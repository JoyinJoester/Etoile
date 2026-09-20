package takagi.ru.monica.github.domain

/**
 * A user-defined collection of starred repositories. Labels are created by the
 * user rather than shipped as fixed topics, and a repository may carry several.
 */
data class GithubStarLabel(val id: Long, val name: String)

/** The label filter applied to the starred list. */
sealed interface GithubStarFilter {
    data object All : GithubStarFilter
    data object Unlabeled : GithubStarFilter
    data class Label(val labelId: Long) : GithubStarFilter
}

/** A starred repository together with the labels the user assigned to it. */
data class GithubLabeledStar(
    val repository: GithubRepository,
    val labels: List<GithubStarLabel>
) {
    fun matches(filter: GithubStarFilter): Boolean = when (filter) {
        GithubStarFilter.All -> true
        GithubStarFilter.Unlabeled -> labels.isEmpty()
        is GithubStarFilter.Label -> labels.any { it.id == filter.labelId }
    }
}

/** Why a label name was rejected. */
enum class GithubStarLabelError { BLANK_NAME, NAME_TOO_LONG, DUPLICATE_NAME, LIMIT_REACHED }

class GithubStarLabelException(val error: GithubStarLabelError) : Exception(error.name)

interface GithubStarsRepository {
    suspend fun starredRepositories(
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubRepository>>
}

/**
 * Stores the user's labels and their assignments. Implementations persist
 * locally because GitHub exposes no API for private starred collections.
 */
interface GithubStarLabelStore {
    fun labels(): List<GithubStarLabel>
    fun labelsFor(repositoryId: Long): List<GithubStarLabel>
    fun createLabel(name: String): Result<GithubStarLabel>
    fun renameLabel(labelId: Long, name: String): Result<GithubStarLabel>
    fun deleteLabel(labelId: Long)

    /** Adds or removes one label on one repository, returning the resulting set. */
    fun toggleAssignment(repositoryId: Long, labelId: Long): List<GithubStarLabel>

    /**
     * Applies one label to many repositories in a single write, returning the
     * resulting sets keyed by repository id. Batching matters because the whole
     * label graph is persisted as one document per write.
     */
    fun assignLabel(
        repositoryIds: Set<Long>,
        labelId: Long,
        assigned: Boolean
    ): Map<Long, List<GithubStarLabel>>
}

/** Normalizes a label name and rejects blank, overlong, or duplicate entries. */
fun validateStarLabelName(
    rawName: String,
    existing: List<GithubStarLabel>,
    ignoreLabelId: Long? = null
): Result<String> {
    val name = rawName.trim()
    if (name.isEmpty()) {
        return Result.failure(GithubStarLabelException(GithubStarLabelError.BLANK_NAME))
    }
    if (name.length > MAX_STAR_LABEL_NAME_LENGTH) {
        return Result.failure(GithubStarLabelException(GithubStarLabelError.NAME_TOO_LONG))
    }
    if (existing.any { it.id != ignoreLabelId && it.name.equals(name, ignoreCase = true) }) {
        return Result.failure(GithubStarLabelException(GithubStarLabelError.DUPLICATE_NAME))
    }
    return Result.success(name)
}

const val MAX_STAR_LABEL_NAME_LENGTH = 24
const val MAX_STAR_LABELS = 32
