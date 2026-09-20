package takagi.ru.monica.github.domain

data class GithubDiscussionCategory(val id: String, val name: String, val acceptsAnswers: Boolean)
data class GithubDiscussion(
    val id: String, val number: Int, val title: String, val body: String,
    val url: String, val author: String?, val category: GithubDiscussionCategory,
    val comments: Int, val answered: Boolean, val canEdit: Boolean = false
)
data class GithubDiscussionPage(val repositoryId: String, val items: List<GithubDiscussion>, val nextCursor: String?)
data class GithubDiscussionComment(val id: String, val body: String, val author: String?, val isAnswer: Boolean,
    val canMarkAnswer: Boolean = false, val canUnmarkAnswer: Boolean = false,
    val canEdit: Boolean = false, val canDelete: Boolean = false)
data class GithubDiscussionComments(val items: List<GithubDiscussionComment>, val nextCursor: String?)

interface GithubDiscussionsRepository {
    suspend fun editComment(id: String, body: String): Result<GithubDiscussionComment> =
        Result.failure(UnsupportedOperationException("Comment editing unavailable"))
    suspend fun deleteComment(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Comment deletion unavailable"))
    suspend fun replies(commentId: String, cursor: String? = null): Result<GithubDiscussionComments> =
        Result.failure(UnsupportedOperationException("Comment replies unavailable"))
    suspend fun replyToComment(discussionId: String, commentId: String, body: String): Result<String> =
        Result.failure(UnsupportedOperationException("Comment replies unavailable"))
    suspend fun edit(id: String, title: String, body: String): Result<GithubDiscussion> =
        Result.failure(UnsupportedOperationException("Discussion editing unavailable"))
    suspend fun detail(owner: String, name: String, number: Int): Result<GithubDiscussion> =
        Result.failure(UnsupportedOperationException("Discussion detail unavailable"))
    suspend fun markAnswer(commentId: String, answered: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Answer management unavailable"))
    suspend fun comments(id: String, cursor: String? = null): Result<GithubDiscussionComments> =
        Result.failure(UnsupportedOperationException("Discussion comments unavailable"))
    suspend fun list(owner: String, name: String, cursor: String? = null): Result<GithubDiscussionPage>
    suspend fun categories(owner: String, name: String): Result<List<GithubDiscussionCategory>>
    suspend fun create(repositoryId: String, categoryId: String, title: String, body: String): Result<GithubDiscussion>
    suspend fun reply(discussionId: String, body: String): Result<String>
}
