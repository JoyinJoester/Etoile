package takagi.ru.monica.github.navigation

import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import takagi.ru.monica.github.di.GithubConversationDependencies
import takagi.ru.monica.github.domain.*

/** No credentials, storage or HTTP clients. Unused service calls fail instead of fabricating data. */
internal class ConversationNavigationFixtures : GithubConversationDependencies {
    val sessions = MutableStateFlow<GithubSession>(signedIn(1))
    val discussionBindings = mutableListOf<Long?>()
    val discussionReads = mutableListOf<Long?>()
    val discussionWrites = mutableListOf<String>()
    val pullRequestWrites = mutableListOf<GithubCreatePullRequestDraft>()
    var createPullRequest: suspend (GithubCreatePullRequestDraft) -> Result<GithubPullRequest> = { Result.success(createdPr()) }
    override val authRepository = object : GithubAuthRepository {
        override val session = sessions
        override val accounts = MutableStateFlow(listOf(signedIn(1).account, signedIn(2).account))
        override suspend fun restore() = Result.success(Unit)
        override suspend fun signInWithToken(token: String) = error("No credentials in navigation tests")
        override suspend fun switchAccount(accountId: Long): Result<GithubAccount> {
            val session = signedIn(accountId); sessions.value = session; return Result.success(session.account)
        }
        override suspend fun removeAccount(accountId: Long) = error("unused")
        override suspend fun signOut() { sessions.value = GithubSession.SignedOut }
    }
    override val discussionsRepository: GithubDiscussionsRepository = Discussions()
    override val issuesRepository = object : GithubIssuesRepository by unavailableService<GithubIssuesRepository>() {
        override suspend fun comments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubIssueComment>(emptyList(), null))
    }
    override val issueTemplatesRepository: GithubIssueTemplatesRepository = unavailableService()
    override val repositoryDetailsRepository = TestGithubRepositoryDetailsRepository()
    override val repositoryContentsRepository = object : GithubRepositoryContentsRepository {
        override suspend fun branches(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(GithubBranch("main", "base", false), GithubBranch("feature", "head", false)), null))
        override suspend fun tags(owner: String, name: String, page: Int, perPage: Int) = Result.success(GithubPage<GithubTag>(emptyList(), null))
        override suspend fun directory(owner: String, name: String, path: String, ref: String?) = Result.success(emptyList<GithubContentItem>())
        override suspend fun file(owner: String, name: String, path: String, ref: String?) = Result.success<GithubFileContent>(GithubFileContent.Text(""))
    }
    override val pullRequestsRepository = object : GithubPullRequestsRepository by unavailableService<GithubPullRequestsRepository>() {
        override suspend fun pullRequests(owner: String, name: String, query: GithubPullRequestListQuery, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubPullRequest>(emptyList(), null))
        override suspend fun create(owner: String, name: String, draft: GithubCreatePullRequestDraft): Result<GithubPullRequest> {
            pullRequestWrites += draft
            return createPullRequest(draft)
        }
        override suspend fun pullRequest(owner: String, name: String, number: Int) = Result.success(createdPr())
        override suspend fun files(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubPullRequestFile>(emptyList(), null))
        override suspend fun reviews(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubPullRequestReview>(emptyList(), null))
        override suspend fun reviewComments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubPullRequestReviewComment>(emptyList(), null))
        override suspend fun reviewThreadStatuses(owner: String, name: String, number: Int, cursor: String?) =
            Result.success(GithubReviewThreadPage(emptyList(), null))
    }

    private inner class Discussions(private val accountId: Long? = null) : GithubDiscussionsRepository {
        override fun forAccount(accountId: Long?): GithubDiscussionsRepository {
            discussionBindings += accountId
            return Discussions(accountId)
        }
        private fun discussion(number: Int = 7) = GithubDiscussion("D$number", number, "Discussion account $accountId", "Private body account $accountId",
            "https://github.com/o/r/discussions/$number", "same-login", GithubDiscussionCategory("Q", "Questions", true), 1, false, true)
        override suspend fun detail(owner: String, name: String, number: Int): Result<GithubDiscussion> {
            discussionReads += accountId; return Result.success(discussion(number))
        }
        override suspend fun list(owner: String, name: String, cursor: String?) = Result.success(GithubDiscussionPage("R", listOf(discussion()), null))
        override suspend fun categories(owner: String, name: String) = Result.success(listOf(GithubDiscussionCategory("Q", "Questions", true)))
        override suspend fun comments(id: String, cursor: String?) = Result.success(GithubDiscussionComments(listOf(
            GithubDiscussionComment("C", "Candidate answer account $accountId", "same-login", false,
                canMarkAnswer = true, canUnmarkAnswer = true, canEdit = true, canDelete = true)), null))
        override suspend fun replies(commentId: String, cursor: String?) = Result.success(GithubDiscussionComments(emptyList(), null))
        override suspend fun create(repositoryId: String, categoryId: String, title: String, body: String): Result<GithubDiscussion> {
            discussionWrites += "create:$accountId"; return Result.success(discussion().copy(title = title, body = body))
        }
        override suspend fun edit(id: String, title: String, body: String): Result<GithubDiscussion> {
            discussionWrites += "edit:$accountId"; return Result.success(discussion().copy(title = title, body = body))
        }
        override suspend fun markAnswer(commentId: String, answered: Boolean): Result<Unit> {
            discussionWrites += "answer:$accountId"; return Result.success(Unit)
        }
        override suspend fun deleteComment(id: String): Result<Unit> {
            discussionWrites += "delete:$accountId"; return Result.success(Unit)
        }
        override suspend fun reply(discussionId: String, body: String): Result<String> {
            discussionWrites += "reply:$accountId"; return Result.success("new")
        }
    }

    companion object {
        fun signedIn(id: Long) = GithubSession.SignedIn(GithubAccount(id, "same-login", null, null, "", "", 0, 0, 0))
        fun createdPr() = GithubPullRequest(10, 10, "Created pull request", "Body", GithubPullRequestState.OPEN,
            true, false, null, null, GithubUserSummary("same-login", null, "https://github.com/same-login"),
            emptyList(), emptyList(), emptyList(), GithubPullRequestRef("feature", "feature", "head", "o/r"),
            GithubPullRequestRef("main", "main", "base", "o/r"), 0, 0, 1, 1, 0, 1,
            "", "", null, null, "https://github.com/o/r/pull/10")

        private inline fun <reified T> unavailableService(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "Unavailable navigation test service"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> throw UnsupportedOperationException("Not part of this navigation scenario: ${method.name}")
            }
        } as T
    }
}
