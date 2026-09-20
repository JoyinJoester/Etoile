package takagi.ru.monica.github.feature.discussions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.GithubDiscussionComment
import takagi.ru.monica.github.domain.GithubDiscussionComments

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionCommentsLoaderTest {
    private fun comment(id: String, body: String = "old") = GithubDiscussionComment(id, body, "user", false)
    private fun page(vararg comments: GithubDiscussionComment, cursor: String? = null) =
        Result.success(GithubDiscussionComments(comments.toList(), cursor))

    @Test fun lateRefreshCannotUndoAnEditOrResurrectADeletedComment() = runTest {
        var response = CompletableDeferred(page(comment("1"), comment("2")))
        val loader = DiscussionCommentsLoader(this) { response.await() }
        loader.fetch(true); runCurrent()
        response = CompletableDeferred()
        loader.fetch(true); runCurrent()
        loader.edited(comment("1", "saved edit")); loader.deleted("2")
        response.complete(page(comment("1"), comment("2")))
        runCurrent()
        assertEquals(listOf(comment("1", "saved edit")), loader.state.value.items)
        assertFalse(loader.state.value.loading)
    }

    @Test fun refreshSupersedesAnUncooperativeOldRequest() = runTest {
        val old = CompletableDeferred<Result<GithubDiscussionComments>>()
        var calls = 0
        val loader = DiscussionCommentsLoader(this) {
            if (++calls == 1) withContext(NonCancellable) { old.await() } else page(comment("new"))
        }
        loader.fetch(true); runCurrent()
        loader.fetch(true); runCurrent()
        assertEquals("new", loader.state.value.items.single().id)
        old.complete(page(comment("stale"))); runCurrent()
        assertEquals("new", loader.state.value.items.single().id)
        assertFalse(loader.state.value.loading)
    }

    @Test fun paginationDeduplicatesAndStopsRepeatedCursorWithoutRestoringDeletedItems() = runTest {
        var response = CompletableDeferred(page(comment("1"), cursor = "next"))
        val loader = DiscussionCommentsLoader(this) { response.await() }
        loader.fetch(true); runCurrent()
        response = CompletableDeferred()
        loader.fetch(false); runCurrent()
        loader.deleted("1")
        response.complete(page(comment("1"), comment("2"), comment("2"), cursor = "next")); runCurrent()
        assertEquals(listOf(comment("2")), loader.state.value.items)
        assertNull(loader.state.value.cursor)
    }

    @Test fun failedRefreshPreservesCurrentContentAndAllowsRetry() = runTest {
        var response = page(comment("1"))
        val loader = DiscussionCommentsLoader(this) { response }
        loader.fetch(true); runCurrent()
        response = Result.failure(java.io.IOException("offline"))
        loader.fetch(true); runCurrent()
        assertTrue(loader.state.value.failed)
        assertEquals(listOf(comment("1")), loader.state.value.items)
        response = page(comment("2"))
        loader.fetch(true); runCurrent()
        assertFalse(loader.state.value.failed)
        assertEquals(listOf(comment("2")), loader.state.value.items)
    }

    @Test fun aLaterRefreshCanReadSubsequentServerEdits() = runTest {
        var response = page(comment("1"))
        val loader = DiscussionCommentsLoader(this) { response }
        loader.fetch(true); runCurrent()
        loader.edited(comment("1", "local edit"))
        response = page(comment("1", "newer server edit"))
        loader.fetch(true); runCurrent()
        assertEquals("newer server edit", loader.state.value.items.single().body)
    }
}
