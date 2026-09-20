package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import takagi.ru.monica.github.domain.*

class GithubDiscussionsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient,
    private val endpoint: String = "https://api.github.com/graphql"
) : GithubDiscussionsRepository {
    override suspend fun editComment(id: String, body: String): Result<GithubDiscussionComment> {
        if (id.isBlank() || body.isBlank() || body.length > 65536) {
            return Result.failure(IllegalArgumentException("Invalid comment"))
        }
        return execute("mutation(\$input:UpdateDiscussionCommentInput!){updateDiscussionComment(input:\$input){comment{$COMMENT_FIELDS}}}",
            buildJsonObject { put("input", buildJsonObject { put("commentId", id); put("body", body) }) }) { data ->
            val value = data.getValue("updateDiscussionComment").jsonObject.getValue("comment").jsonObject
            GithubDiscussionComment(value.text("id"), value.text("body"),
                value["author"]?.takeUnless { it is JsonNull }?.jsonObject?.text("login"),
                value.getValue("isAnswer").jsonPrimitive.boolean,
                value["viewerCanMarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUnmarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUpdate"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanDelete"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
    }

    override suspend fun deleteComment(id: String): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("Invalid comment ID"))
        return execute("mutation(\$input:DeleteDiscussionCommentInput!){deleteDiscussionComment(input:\$input){clientMutationId}}",
            buildJsonObject { put("input", buildJsonObject { put("id", id) }) }) { data ->
            data.getValue("deleteDiscussionComment").jsonObject
            Unit
        }
    }
    override suspend fun replies(commentId: String, cursor: String?): Result<GithubDiscussionComments> = execute(
        "query(\$id:ID!,\$cursor:String){node(id:\$id){... on DiscussionComment{replies(first:30,after:\$cursor){nodes{$COMMENT_FIELDS} pageInfo{hasNextPage endCursor}}}}}",
        buildJsonObject { put("id", commentId); put("cursor", cursor) }
    ) { data ->
        val connection = data.getValue("node").jsonObject.getValue("replies").jsonObject
        val page = connection.getValue("pageInfo").jsonObject
        GithubDiscussionComments(connection.getValue("nodes").jsonArray.map { element ->
            val value = element.jsonObject
            GithubDiscussionComment(value.text("id"), value.text("body"),
                value["author"]?.takeUnless { it is JsonNull }?.jsonObject?.text("login"),
                value.getValue("isAnswer").jsonPrimitive.boolean,
                value["viewerCanMarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUnmarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUpdate"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanDelete"]?.jsonPrimitive?.booleanOrNull ?: false)
        }, if (page.getValue("hasNextPage").jsonPrimitive.boolean) page.text("endCursor") else null)
    }

    override suspend fun replyToComment(discussionId: String, commentId: String, body: String): Result<String> {
        if (discussionId.isBlank() || commentId.isBlank() || body.isBlank() || body.length > 65536) {
            return Result.failure(IllegalArgumentException("Invalid reply"))
        }
        return execute("mutation(\$input:AddDiscussionCommentInput!){addDiscussionComment(input:\$input){comment{id}}}",
            buildJsonObject { put("input", buildJsonObject {
                put("discussionId", discussionId); put("replyToId", commentId); put("body", body)
            }) }) { it.getValue("addDiscussionComment").jsonObject.getValue("comment").jsonObject.text("id") }
    }
    override suspend fun edit(id: String, title: String, body: String): Result<GithubDiscussion> {
        if (id.isBlank() || title.isBlank() || title.length > 256 || body.length > 65536) {
            return Result.failure(IllegalArgumentException("Invalid discussion"))
        }
        return execute("mutation(\$input:UpdateDiscussionInput!){updateDiscussion(input:\$input){discussion{$FIELDS}}}",
            buildJsonObject { put("input", buildJsonObject {
                put("discussionId", id); put("title", title.trim()); put("body", body)
            }) }) { discussion(it.getValue("updateDiscussion").jsonObject.getValue("discussion").jsonObject) }
    }

    override suspend fun detail(owner: String, name: String, number: Int): Result<GithubDiscussion> {
        if (number <= 0) return Result.failure(IllegalArgumentException("Invalid discussion number"))
        return execute("query(\$owner:String!,\$name:String!,\$number:Int!){repository(owner:\$owner,name:\$name){discussion(number:\$number){$FIELDS}}}",
            buildJsonObject { put("owner", owner); put("name", name); put("number", number) }) {
            discussion(it.getValue("repository").jsonObject.getValue("discussion").jsonObject)
        }
    }
    override suspend fun markAnswer(commentId: String, answered: Boolean): Result<Unit> {
        if (commentId.isBlank()) return Result.failure(IllegalArgumentException("Invalid comment ID"))
        val mutation = if (answered) "markDiscussionCommentAsAnswer" else "unmarkDiscussionCommentAsAnswer"
        val input = if (answered) "MarkDiscussionCommentAsAnswerInput" else "UnmarkDiscussionCommentAsAnswerInput"
        return execute("mutation(\$input:$input!){$mutation(input:\$input){discussion{id}}}",
            buildJsonObject { put("input", buildJsonObject { put("id", commentId) }) }) { data ->
            data.getValue(mutation).jsonObject.getValue("discussion").jsonObject.text("id")
            Unit
        }
    }
    override suspend fun comments(id: String, cursor: String?): Result<GithubDiscussionComments> = execute(
        "query(\$id:ID!,\$cursor:String){node(id:\$id){... on Discussion{comments(first:30,after:\$cursor){nodes{$COMMENT_FIELDS} pageInfo{hasNextPage endCursor}}}}}",
        buildJsonObject { put("id", id); put("cursor", cursor) }
    ) { data ->
        val connection = data.getValue("node").jsonObject.getValue("comments").jsonObject
        val page = connection.getValue("pageInfo").jsonObject
        GithubDiscussionComments(connection.getValue("nodes").jsonArray.map { element ->
            val value = element.jsonObject
            GithubDiscussionComment(value.text("id"), value.text("body"),
                value["author"]?.takeUnless { it is JsonNull }?.jsonObject?.text("login"),
                value.getValue("isAnswer").jsonPrimitive.boolean,
                value["viewerCanMarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUnmarkAsAnswer"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanUpdate"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["viewerCanDelete"]?.jsonPrimitive?.booleanOrNull ?: false)
        }, if (page.getValue("hasNextPage").jsonPrimitive.boolean) page.text("endCursor") else null)
    }
    override suspend fun list(owner: String, name: String, cursor: String?): Result<GithubDiscussionPage> = execute(
        "query(\$owner:String!,\$name:String!,\$cursor:String){repository(owner:\$owner,name:\$name){id discussions(first:30,after:\$cursor,orderBy:{field:UPDATED_AT,direction:DESC}){nodes{$FIELDS} pageInfo{hasNextPage endCursor}}}}",
        buildJsonObject { put("owner", owner); put("name", name); put("cursor", cursor) }
    ) { data ->
        val repo = data.getValue("repository").jsonObject
        val connection = repo.getValue("discussions").jsonObject
        val page = connection.getValue("pageInfo").jsonObject
        GithubDiscussionPage(repo.text("id"), connection.getValue("nodes").jsonArray.map { discussion(it.jsonObject) },
            if (page.getValue("hasNextPage").jsonPrimitive.boolean) page.text("endCursor") else null)
    }

    override suspend fun categories(owner: String, name: String): Result<List<GithubDiscussionCategory>> = execute(
        "query(\$owner:String!,\$name:String!){repository(owner:\$owner,name:\$name){discussionCategories(first:100){nodes{id name isAnswerable}}}}",
        buildJsonObject { put("owner", owner); put("name", name) }
    ) { data -> data.getValue("repository").jsonObject.getValue("discussionCategories").jsonObject
        .getValue("nodes").jsonArray.map { category(it.jsonObject) } }

    override suspend fun create(repositoryId: String, categoryId: String, title: String, body: String): Result<GithubDiscussion> {
        if (title.isBlank() || title.length > 256 || body.isBlank() || body.length > 65536) return Result.failure(IllegalArgumentException("Invalid discussion"))
        return execute("mutation(\$input:CreateDiscussionInput!){createDiscussion(input:\$input){discussion{$FIELDS}}}",
            buildJsonObject { put("input", buildJsonObject {
                put("repositoryId", repositoryId); put("categoryId", categoryId); put("title", title.trim()); put("body", body)
            }) }) { discussion(it.getValue("createDiscussion").jsonObject.getValue("discussion").jsonObject) }
    }

    override suspend fun reply(discussionId: String, body: String): Result<String> {
        if (body.isBlank() || body.length > 65536) return Result.failure(IllegalArgumentException("Invalid reply"))
        return execute("mutation(\$input:AddDiscussionCommentInput!){addDiscussionComment(input:\$input){comment{id}}}",
            buildJsonObject { put("input", buildJsonObject { put("discussionId", discussionId); put("body", body) }) }
        ) { it.getValue("addDiscussionComment").jsonObject.getValue("comment").jsonObject.text("id") }
    }

    private suspend fun <T> execute(query: String, variables: JsonObject, decode: (JsonObject) -> T): Result<T> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val body = buildJsonObject { put("query", query); put("variables", variables) }
            val request = requests.builder(endpoint).post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val payload = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                if (payload["errors"]?.jsonArray?.isNotEmpty() == true) throw GithubDiscussionException()
                decode(payload.getValue("data").jsonObject)
            }
        }
    }

    private fun category(value: JsonObject) = GithubDiscussionCategory(value.text("id"), value.text("name"), value.getValue("isAnswerable").jsonPrimitive.boolean)
    private fun discussion(value: JsonObject) = GithubDiscussion(value.text("id"), value.getValue("number").jsonPrimitive.int,
        value.text("title"), value.text("body"), value.text("url"),
        value["author"]?.takeUnless { it is JsonNull }?.jsonObject?.text("login"),
        category(value.getValue("category").jsonObject), value.getValue("comments").jsonObject.getValue("totalCount").jsonPrimitive.int,
        value["answer"]?.let { it !is JsonNull } == true,
        value["viewerCanUpdate"]?.jsonPrimitive?.booleanOrNull ?: false)
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private companion object {
        const val COMMENT_FIELDS = "id body author{login} isAnswer viewerCanMarkAsAnswer viewerCanUnmarkAsAnswer viewerCanUpdate viewerCanDelete"
        const val FIELDS = "viewerCanUpdate id number title body url author{login} category{id name isAnswerable} comments{totalCount} answer{id}"
    }
}

class GithubDiscussionException : IllegalStateException("GitHub discussion request failed")
