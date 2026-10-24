package takagi.ru.monica.gitlab.domain

enum class GitLabConversationKind(val apiPath: String) { ISSUE("issues"), MERGE_REQUEST("merge_requests") }
enum class GitLabWorkSection { ISSUES, MERGE_REQUESTS, PIPELINES }

data class GitLabConversation(
    val id: Long,
    val number: Long,
    val title: String,
    val description: String,
    val state: String,
    val author: String,
    val authorId: Long,
    val updatedAt: String,
    val discussionLocked: Boolean = false,
    val sourceBranch: String = "",
    val targetBranch: String = "",
    val sha: String = "",
    val draft: Boolean = false,
    val mergeStatus: String = "",
    val webUrl: String = ""
) {
    val canMerge: Boolean get() = state == "opened" && !draft && mergeStatus == "mergeable" &&
        sha.length in listOf(40, 64) && sha.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } && sourceBranch.isNotBlank() && targetBranch.isNotBlank()
}

fun gitLabHasQuickActions(body: String): Boolean = body.lineSequence().any { it.trimStart().startsWith("/") }

data class GitLabConversationDraft(val title: String, val description: String, val sourceBranch: String = "", val targetBranch: String = "",
    val allowQuickActions: Boolean = false) {
    fun isValid(kind: GitLabConversationKind, creating: Boolean): Boolean = title.trim().length in 1..255 &&
        description.length <= 65_536 && (!creating || kind == GitLabConversationKind.ISSUE ||
        (sourceBranch.isNotBlank() && targetBranch.isNotBlank() && sourceBranch.trim() != targetBranch.trim() &&
            sourceBranch.length <= 1024 && targetBranch.length <= 1024 &&
            (sourceBranch + targetBranch).none { it.isISOControl() }))
}

data class GitLabNote(val id: Long, val body: String, val author: String, val createdAt: String, val system: Boolean)
data class GitLabMergeTarget(val number: Long, val sha: String, val sourceBranch: String, val targetBranch: String)
data class GitLabDiff(val oldPath: String, val newPath: String, val patch: String, val unavailable: Boolean)
data class GitLabPipeline(val id: Long, val ref: String, val sha: String, val status: String, val updatedAt: String) {
    val canCancel: Boolean get() = status in setOf("created", "waiting_for_resource", "preparing", "pending", "running", "scheduled")
    val canRetry: Boolean get() = status in setOf("failed", "canceled")
}
data class GitLabJob(val id: Long, val pipelineId: Long, val name: String, val stage: String, val status: String) {
    val canPlay: Boolean get() = status == "manual"
    val canCancel: Boolean get() = status in setOf("created", "pending", "preparing", "running", "waiting_for_resource", "scheduled")
    val canRetry: Boolean get() = status in setOf("failed", "canceled", "success")
}
enum class GitLabRunAction(val apiPath: String) { RETRY("retry"), CANCEL("cancel"), PLAY("play") }

interface GitLabWorkRepository {
    suspend fun conversations(key: String, projectId: Long, kind: GitLabConversationKind, state: String, search: String, page: Int = 1): Result<GitLabPage<GitLabConversation>>
    suspend fun conversation(key: String, projectId: Long, kind: GitLabConversationKind, number: Long): Result<GitLabConversation>
    suspend fun create(key: String, projectId: Long, kind: GitLabConversationKind, draft: GitLabConversationDraft): Result<GitLabConversation>
    suspend fun edit(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, draft: GitLabConversationDraft): Result<GitLabConversation>
    suspend fun changeState(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, close: Boolean): Result<GitLabConversation>
    suspend fun notes(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, page: Int = 1): Result<GitLabPage<GitLabNote>>
    suspend fun comment(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, body: String, allowQuickActions: Boolean = false): Result<GitLabNote>
    suspend fun diffs(key: String, projectId: Long, number: Long, expectedSha: String, page: Int = 1): Result<GitLabPage<GitLabDiff>>
    suspend fun merge(key: String, projectId: Long, target: GitLabMergeTarget): Result<GitLabConversation>
    suspend fun pipelines(key: String, projectId: Long, ref: String, page: Int = 1): Result<GitLabPage<GitLabPipeline>>
    suspend fun pipeline(key: String, projectId: Long, id: Long): Result<GitLabPipeline>
    suspend fun jobs(key: String, projectId: Long, pipelineId: Long, page: Int = 1): Result<GitLabPage<GitLabJob>>
    suspend fun trace(key: String, projectId: Long, pipelineId: Long, jobId: Long): Result<String>
    suspend fun runPipeline(key: String, projectId: Long, ref: String): Result<GitLabPipeline>
    suspend fun pipelineAction(key: String, projectId: Long, id: Long, action: GitLabRunAction): Result<GitLabPipeline>
    suspend fun jobAction(key: String, projectId: Long, pipelineId: Long, jobId: Long, action: GitLabRunAction): Result<GitLabJob>
}
