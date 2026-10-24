package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubReviewThreadPage
import takagi.ru.monica.github.domain.GithubReviewThreadStatus

internal class GithubReviewThreadsApi(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient,
    private val endpoint: String,
    private val cache: GithubCacheStore
) {
    suspend fun load(owner: String, name: String, number: Int, cursor: String?): Result<GithubReviewThreadPage> =
        execute("query(\$owner:String!,\$name:String!,\$number:Int!,\$cursor:String){repository(owner:\$owner,name:\$name){pullRequest(number:\$number){reviewThreads(first:50,after:\$cursor){nodes{$FIELDS} pageInfo{hasNextPage endCursor}}}}}",
            buildJsonObject { put("owner", owner); put("name", name); put("number", number); put("cursor", cursor) }) { data ->
            val connection = data.getValue("repository").jsonObject.getValue("pullRequest").jsonObject.getValue("reviewThreads").jsonObject
            val page = connection.getValue("pageInfo").jsonObject
            val next = if (page.getValue("hasNextPage").jsonPrimitive.boolean) {
                page.getValue("endCursor").jsonPrimitive.content.also { require(it.isNotBlank() && it != cursor && it != "null") }
            } else null
            GithubReviewThreadPage(connection.getValue("nodes").jsonArray.map { decode(it.jsonObject) }, next)
        }

    suspend fun setResolved(id: String, resolved: Boolean): Result<GithubReviewThreadStatus> {
        if (id.isBlank() || id.length > 512) return Result.failure(IllegalArgumentException("Invalid thread"))
        val operation = if (resolved) "resolveReviewThread" else "unresolveReviewThread"
        val input = if (resolved) "ResolveReviewThreadInput" else "UnresolveReviewThreadInput"
        return execute("mutation(\$input:$input!){$operation(input:\$input){thread{$FIELDS}}}",
            buildJsonObject { put("input", buildJsonObject { put("threadId", id) }) }, write = true) { data ->
            decode(data.getValue(operation).jsonObject.getValue("thread").jsonObject).also {
                require(it.id == id && it.isResolved == resolved)
            }
        }
    }

    private suspend fun <T> execute(query: String, variables: JsonObject, write: Boolean = false, decode: (JsonObject) -> T): Result<T> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = buildJsonObject { put("query", query); put("variables", variables) }
            val request = requests.builder(endpoint).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.readGithubResponse(request) { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val value = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                // A GraphQL 200 can contain partial data plus errors. Never treat that as a successful mutation.
                require(value["errors"]?.jsonArray?.isNotEmpty() != true) { "Review thread request failed" }
                if (write) cache.invalidateAfter { decode(value.getValue("data").jsonObject) }
                else decode(value.getValue("data").jsonObject)
            }
        }
    }

    private fun decode(value: JsonObject): GithubReviewThreadStatus {
        val root = value.getValue("comments").jsonObject.getValue("nodes").jsonArray.first().jsonObject
            .getValue("fullDatabaseId").jsonPrimitive.long
        require(root > 0)
        return GithubReviewThreadStatus(value.getValue("id").jsonPrimitive.content.also { require(it.isNotBlank()) }, root,
            value.getValue("isResolved").jsonPrimitive.boolean, value.getValue("isOutdated").jsonPrimitive.boolean,
            value.getValue("viewerCanResolve").jsonPrimitive.boolean, value.getValue("viewerCanUnresolve").jsonPrimitive.boolean)
    }

    private companion object {
        const val FIELDS = "id isResolved isOutdated viewerCanResolve viewerCanUnresolve comments(first:1){nodes{fullDatabaseId}}"
    }
}
