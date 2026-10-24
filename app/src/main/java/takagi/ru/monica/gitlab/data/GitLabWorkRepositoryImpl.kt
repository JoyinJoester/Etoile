package takagi.ru.monica.gitlab.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.gitlab.domain.*

internal class GitLabWorkRepositoryImpl(
    client: OkHttpClient,
    credentials: GitLabCredentialProvider,
    instanceUrl: (String) -> HttpUrl = ::gitLabInstanceUrl
) : GitLabWorkRepository {
    private val api = GitLabApiClient(client, credentials, instanceUrl)
    private val json = Json { ignoreUnknownKeys = true }
    private fun path(id: Long) = GitLabApiClient.projectPath(id)
    private fun itemPath(id: Long, kind: GitLabConversationKind, number: Long): List<String> {
        require(number > 0); return path(id) + listOf(kind.apiPath, number.toString())
    }
    private fun runPath(id: Long, pipelineId: Long): List<String> { require(pipelineId > 0); return path(id) + listOf("pipelines", "$pipelineId") }
    private fun jobPath(id: Long, jobId: Long): List<String> { require(jobId > 0); return path(id) + listOf("jobs", "$jobId") }
    private fun page(page: Int) = GitLabApiClient.pageParams(page)
    private fun input(valid: Boolean) { if (!valid) throw GitLabReadException(GitLabReadError.INVALID_INPUT) }

    override suspend fun conversations(key: String, projectId: Long, kind: GitLabConversationKind, state: String, search: String, page: Int) = api.result {
        input(state in setOf("all", "opened", "closed") || (kind == GitLabConversationKind.MERGE_REQUEST && state == "merged"))
        input(search.length <= 200)
        val response = api.request(key, path(projectId) + kind.apiPath, page(page) + mapOf(
            "state" to state, "search" to search.trim(), "order_by" to "updated_at", "sort" to "desc", "scope" to "all"))
        GitLabPage(json.decodeFromString<List<ConversationDto>>(response.body).map { it.domain(projectId) }, response.nextPage)
    }

    override suspend fun conversation(key: String, projectId: Long, kind: GitLabConversationKind, number: Long) = api.result {
        readConversation(key, projectId, kind, number)
    }

    private suspend fun readConversation(key: String, projectId: Long, kind: GitLabConversationKind, number: Long): GitLabConversation =
        decodeConversation(api.request(key, itemPath(projectId, kind, number)).body, projectId).also { require(it.number == number) }

    override suspend fun create(key: String, projectId: Long, kind: GitLabConversationKind, draft: GitLabConversationDraft) = api.result {
        input(draft.isValid(kind, creating = true))
        input(!gitLabHasQuickActions(draft.description) || draft.allowQuickActions)
        val body = draftBody(draft, kind, creating = true)
        decodeConversation(api.request(key, path(projectId) + kind.apiPath, method = "POST", body = body).body, projectId)
    }

    override suspend fun edit(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, draft: GitLabConversationDraft) = api.result {
        input(draft.isValid(kind, creating = false))
        input(!gitLabHasQuickActions(draft.description) || draft.allowQuickActions)
        readConversation(key, projectId, kind, number).also { input(it.state != "merged") }
        decodeConversation(api.request(key, itemPath(projectId, kind, number), method = "PUT", body = draftBody(draft, kind, creating = false)).body, projectId)
            .also { require(it.number == number) }
    }

    override suspend fun changeState(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, close: Boolean) = api.result {
        val current = readConversation(key, projectId, kind, number)
        input(current.state == if (close) "opened" else "closed")
        val body = buildJsonObject { put("state_event", if (close) "close" else "reopen") }.toString()
        decodeConversation(api.request(key, itemPath(projectId, kind, number), method = "PUT", body = body).body, projectId)
            .also { require(it.number == number && it.state == if (close) "closed" else "opened") }
    }

    override suspend fun notes(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, page: Int) = api.result {
        val response = api.request(key, itemPath(projectId, kind, number) + "notes", page(page) + mapOf("sort" to "desc", "order_by" to "created_at"))
        GitLabPage(json.decodeFromString<List<NoteDto>>(response.body).map(NoteDto::domain), response.nextPage)
    }

    override suspend fun comment(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, body: String, allowQuickActions: Boolean) = api.result {
        input(body.isNotBlank() && body.length <= 65_536)
        input(!gitLabHasQuickActions(body) || allowQuickActions)
        val current = readConversation(key, projectId, kind, number)
        input(!current.discussionLocked)
        val payload = buildJsonObject { put("body", body) }.toString()
        json.decodeFromString<NoteDto>(api.request(key, itemPath(projectId, kind, number) + "notes", method = "POST", body = payload).body).domain()
    }

    override suspend fun diffs(key: String, projectId: Long, number: Long, expectedSha: String, page: Int) = api.result {
        val before = readConversation(key, projectId, GitLabConversationKind.MERGE_REQUEST, number)
        if (expectedSha.isEmpty() || before.sha != expectedSha) throw GitLabReadException(GitLabReadError.CONFLICT)
        val response = api.request(key, itemPath(projectId, GitLabConversationKind.MERGE_REQUEST, number) + "diffs", page(page))
        val after = readConversation(key, projectId, GitLabConversationKind.MERGE_REQUEST, number)
        if (after.sha != expectedSha) throw GitLabReadException(GitLabReadError.CONFLICT)
        GitLabPage(json.decodeFromString<List<DiffDto>>(response.body).map { GitLabDiff(it.oldPath, it.newPath, it.diff.orEmpty(), it.tooLarge || it.collapsed || it.diff.isNullOrEmpty()) }, response.nextPage)
    }

    override suspend fun merge(key: String, projectId: Long, target: GitLabMergeTarget) = api.result {
        val current = readConversation(key, projectId, GitLabConversationKind.MERGE_REQUEST, target.number)
        if (!current.canMerge || current.sha != target.sha || current.sourceBranch != target.sourceBranch || current.targetBranch != target.targetBranch)
            throw GitLabReadException(GitLabReadError.CONFLICT)
        val body = buildJsonObject { put("sha", target.sha); put("should_remove_source_branch", false) }.toString()
        decodeConversation(api.request(key, itemPath(projectId, GitLabConversationKind.MERGE_REQUEST, target.number) + "merge", method = "PUT", body = body).body, projectId)
            .also { require(it.number == target.number && it.state == "merged") }
    }

    override suspend fun pipelines(key: String, projectId: Long, ref: String, page: Int) = api.result {
        validateRef(ref, allowEmpty = true)
        val params = page(page) + mapOf("order_by" to "id", "sort" to "desc") + if (ref.isBlank()) emptyMap() else mapOf("ref" to ref.trim())
        val response = api.request(key, path(projectId) + "pipelines", params)
        GitLabPage(json.decodeFromString<List<PipelineDto>>(response.body).map(PipelineDto::domain), response.nextPage)
    }
    override suspend fun pipeline(key: String, projectId: Long, id: Long) = api.result { readPipeline(key, projectId, id) }
    private suspend fun readPipeline(key: String, projectId: Long, id: Long) =
        json.decodeFromString<PipelineDto>(api.request(key, runPath(projectId, id)).body).domain().also { require(it.id == id) }

    override suspend fun jobs(key: String, projectId: Long, pipelineId: Long, page: Int) = api.result {
        val response = api.request(key, runPath(projectId, pipelineId) + "jobs", page(page) + mapOf("include_retried" to "false"))
        GitLabPage(json.decodeFromString<List<JobDto>>(response.body).map { it.domain().also { job -> require(job.pipelineId == pipelineId) } }, response.nextPage)
    }

    override suspend fun trace(key: String, projectId: Long, pipelineId: Long, jobId: Long) = api.result {
        readJob(key, projectId, pipelineId, jobId)
        api.request(key, jobPath(projectId, jobId) + "trace", maxBytes = 1_048_576, oversized = GitLabReadError.LOG_TOO_LARGE).body
            .replace(Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)"), "")
            .replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
            .filter { !it.isISOControl() || it in "\n\r\t" }
    }

    override suspend fun runPipeline(key: String, projectId: Long, ref: String) = api.result {
        validateRef(ref)
        json.decodeFromString<PipelineDto>(api.request(key, path(projectId) + "pipeline", method = "POST",
            body = buildJsonObject { put("ref", ref.trim()) }.toString()).body).domain()
    }

    override suspend fun pipelineAction(key: String, projectId: Long, id: Long, action: GitLabRunAction) = api.result {
        input(action != GitLabRunAction.PLAY)
        val current = readPipeline(key, projectId, id)
        input(if (action == GitLabRunAction.CANCEL) current.canCancel else current.canRetry)
        json.decodeFromString<PipelineDto>(api.request(key, runPath(projectId, id) + action.apiPath, method = "POST").body).domain()
            .also { require(it.id == id) }
    }

    override suspend fun jobAction(key: String, projectId: Long, pipelineId: Long, jobId: Long, action: GitLabRunAction) = api.result {
        val current = readJob(key, projectId, pipelineId, jobId)
        input(when (action) { GitLabRunAction.PLAY -> current.canPlay; GitLabRunAction.CANCEL -> current.canCancel; GitLabRunAction.RETRY -> current.canRetry })
        json.decodeFromString<JobDto>(api.request(key, jobPath(projectId, jobId) + action.apiPath, method = "POST").body).domain()
            .also { require(it.pipelineId == pipelineId && (action == GitLabRunAction.RETRY || it.id == jobId)) }
    }
    private suspend fun readJob(key: String, projectId: Long, pipelineId: Long, jobId: Long) =
        json.decodeFromString<JobDto>(api.request(key, jobPath(projectId, jobId)).body).domain()
            .also { require(it.id == jobId && it.pipelineId == pipelineId) }

    private fun validateRef(ref: String, allowEmpty: Boolean = false) {
        input((allowEmpty || ref.isNotBlank()) && ref.length <= 1024 && ref.none { it.isISOControl() })
    }
    private fun draftBody(draft: GitLabConversationDraft, kind: GitLabConversationKind, creating: Boolean) = buildJsonObject {
        put("title", draft.title.trim()); put("description", draft.description)
        if (creating && kind == GitLabConversationKind.MERGE_REQUEST) {
            put("source_branch", draft.sourceBranch.trim()); put("target_branch", draft.targetBranch.trim())
        }
    }.toString()
    private fun decodeConversation(body: String, projectId: Long) = json.decodeFromString<ConversationDto>(body).domain(projectId)

    @Serializable private data class UserDto(val id: Long = 0, val username: String = "")
    @Serializable private data class ConversationDto(
        val id: Long, val iid: Long, @SerialName("project_id") val projectId: Long,
        val title: String, val description: String? = null, val state: String,
        val author: UserDto? = null, @SerialName("updated_at") val updatedAt: String = "",
        @SerialName("discussion_locked") val discussionLocked: Boolean? = null,
        @SerialName("source_branch") val sourceBranch: String = "", @SerialName("target_branch") val targetBranch: String = "",
        val sha: String? = null, val draft: Boolean = false, @SerialName("work_in_progress") val workInProgress: Boolean = false,
        @SerialName("has_conflicts") val hasConflicts: Boolean = false,
        @SerialName("detailed_merge_status") val detailedMergeStatus: String? = null,
        @SerialName("merge_status") val mergeStatus: String = "",
        @SerialName("web_url") val webUrl: String? = null
    ) {
        fun domain(expectedProject: Long): GitLabConversation {
            require(id > 0 && iid > 0 && projectId == expectedProject)
            val status = if (hasConflicts) "conflict" else detailedMergeStatus ?: if (mergeStatus == "can_be_merged") "mergeable" else mergeStatus
            return GitLabConversation(id, iid, title, description.orEmpty(), state, author?.username.orEmpty(), author?.id ?: 0,
                updatedAt, discussionLocked == true, sourceBranch, targetBranch, sha.orEmpty(), draft || workInProgress, status, webUrl.orEmpty())
        }
    }
    @Serializable private data class NoteDto(val id: Long, val body: String, val author: UserDto? = null,
        @SerialName("created_at") val createdAt: String = "", val system: Boolean = false) {
        fun domain(): GitLabNote { require(id > 0); return GitLabNote(id, body, author?.username.orEmpty(), createdAt, system) }
    }
    @Serializable private data class DiffDto(@SerialName("old_path") val oldPath: String, @SerialName("new_path") val newPath: String,
        val diff: String? = null, @SerialName("too_large") val tooLarge: Boolean = false, val collapsed: Boolean = false)
    @Serializable private data class PipelineDto(val id: Long, val ref: String, val sha: String, val status: String,
        @SerialName("updated_at") val updatedAt: String = "") {
        fun domain(): GitLabPipeline { require(id > 0); return GitLabPipeline(id, ref, sha, status, updatedAt) }
    }
    @Serializable private data class PipelineIdDto(val id: Long)
    @Serializable private data class JobDto(val id: Long, val name: String, val stage: String, val status: String, val pipeline: PipelineIdDto) {
        fun domain(): GitLabJob { require(id > 0 && pipeline.id > 0); return GitLabJob(id, pipeline.id, name, stage, status) }
    }
}
