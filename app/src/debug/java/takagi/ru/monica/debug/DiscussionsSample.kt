package takagi.ru.monica.debug

import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.discussions.*

@Composable
internal fun DiscussionsSample() {
    val category = GithubDiscussionCategory("C1", "使用交流", true)
    var item by remember { mutableStateOf(GithubDiscussion("D1", 42, "如何在手机上参与项目讨论？",
        "## 使用方法\n\n支持 **Markdown**、列表和代码。\n\n- 打开项目\n- 选择讨论\n\n```kotlin\nprintln(\"Hello Etoile\")\n```",
        "https://github.com/sample/repo/discussions/42", "maintainer", category, 1, false, true)) }
    var state by remember { mutableStateOf(DiscussionsUiState(items = listOf(item), categories = listOf(category), repositoryId = "R1")) }
    var page by remember { mutableStateOf("list") }
    var comments by remember { mutableStateOf(listOf(GithubDiscussionComment("CMT1", "可以点击下方展开回复。", "contributor", false, true, true, true, true))) }
    var replies by remember { mutableStateOf(emptyList<GithubDiscussionComment>()) }
    BackHandler(page != "list") { page = "list" }
    when (page) {
        "compose" -> DiscussionComposer(state, { title, body, id -> state = state.copy(title = title, body = body, categoryId = id) }, {
            item = item.copy(title = state.title, body = state.body)
            state = state.copy(items = listOf(item), title = "", body = "", categoryId = null)
            page = "list"
        }, { page = "list" }, {})
        "detail" -> DiscussionDetailScreen(item, onEdit = { title, body ->
            item = item.copy(title = title, body = body); Result.success(item)
        }, onOpenLink = {}, signedIn = true,
            editComment = { id, body ->
                val updated = (comments + replies).first { it.id == id }.copy(body = body)
                comments = comments.map { if (it.id == id) updated else it }
                replies = replies.map { if (it.id == id) updated else it }
                Result.success(updated)
            },
            deleteComment = { id ->
                comments = comments.filterNot { it.id == id }; replies = replies.filterNot { it.id == id }
                Result.success(Unit)
            },
            loadComments = { Result.success(GithubDiscussionComments(comments, null)) },
            onMarkAnswer = { id, answer -> comments = comments.map { it.copy(isAnswer = it.id == id && answer) }; Result.success(Unit) },
            loadReplies = { _, _ -> Result.success(GithubDiscussionComments(replies, null)) },
            replyToComment = { _, body -> replies = replies + GithubDiscussionComment("R${replies.size}", body, "me", false); Result.success("reply") },
            onReply = { body -> comments = comments + GithubDiscussionComment("C${comments.size}", body, "me", false); Result.success("comment") },
            onBack = { page = "list" })
        else -> DiscussionsScreen(state.copy(items = listOf(item)), {}, {}, {}, { page = "compose" }, { page = "detail" })
    }
}
