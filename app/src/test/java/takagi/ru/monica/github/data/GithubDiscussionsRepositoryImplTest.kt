package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GithubDiscussionsRepositoryImplTest {
    @Test fun commentEditingAndDeletionValidateInputsAndReadServerPermissions() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            assertTrue(repository.editComment("", "body").isFailure)
            assertTrue(repository.editComment("C1", " ").isFailure)
            assertTrue(repository.editComment("C1", "x".repeat(65537)).isFailure)
            assertTrue(repository.deleteComment(" ").isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"data":{"updateDiscussionComment":{"comment":{"id":"C1","body":"Updated","author":null,"isAnswer":true,"viewerCanUpdate":true,"viewerCanDelete":false}}}}"""))
            val updated = repository.editComment("C1", "Updated").getOrThrow()
            assertEquals("Updated", updated.body)
            assertTrue(updated.canEdit)
            assertFalse(updated.canDelete)
            assertTrue(updated.isAnswer)
            assertNull(updated.author)
            val edit = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(edit.getValue("query").jsonPrimitive.content.contains("UpdateDiscussionCommentInput"))
            val input = edit.getValue("variables").jsonObject.getValue("input").jsonObject
            assertEquals("C1", input.getValue("commentId").jsonPrimitive.content)
            assertEquals("Updated", input.getValue("body").jsonPrimitive.content)
            server.enqueue(MockResponse().setBody("""{"data":{"deleteDiscussionComment":{"clientMutationId":null}}}"""))
            assertTrue(repository.deleteComment("C1").isSuccess)
            val delete = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(delete.getValue("query").jsonPrimitive.content.contains("DeleteDiscussionCommentInput"))
            assertEquals("C1", delete.getValue("variables").jsonObject.getValue("input").jsonObject.getValue("id").jsonPrimitive.content)
            server.enqueue(MockResponse().setBody("""{"data":{"deleteDiscussionComment":null},"errors":[{"message":"forbidden"}]}"""))
            assertTrue(repository.deleteComment("C1").exceptionOrNull() is GithubDiscussionException)
            server.enqueue(MockResponse().setBody("""{"data":{"updateDiscussionComment":null},"errors":[{"message":"forbidden"}]}"""))
            assertTrue(repository.editComment("C1", "Retry").exceptionOrNull() is GithubDiscussionException)
        }
    }

    @Test fun bothCommentLevelsRequestAndMapMutationPermissions() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            for (connection in listOf("comments", "replies")) {
                server.enqueue(MockResponse().setBody("""{"data":{"node":{"$connection":{"nodes":[{"id":"C1","body":"body","author":null,"isAnswer":false,"viewerCanUpdate":true,"viewerCanDelete":true}],"pageInfo":{"hasNextPage":false,"endCursor":null}}}}}"""))
                val page = if (connection == "comments") repository.comments("D1") else repository.replies("C1")
                assertTrue(page.getOrThrow().items.single().canEdit)
                assertTrue(page.getOrThrow().items.single().canDelete)
                val query = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("query").jsonPrimitive.content
                assertTrue(query.contains("viewerCanUpdate"))
                assertTrue(query.contains("viewerCanDelete"))
            }
        }
    }

    private val store = object : GithubTokenStore {
        override fun read() = "test-token-12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    @Test fun nestedRepliesSendParentIdAndPageWithinComment() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            assertTrue(repository.replyToComment("D1", "", "hello").isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"data":{"addDiscussionComment":{"comment":{"id":"R1"}}}}"""))
            assertEquals("R1", repository.replyToComment("D1", "C1", "hello").getOrThrow())
            val input = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject.getValue("input").jsonObject
            assertEquals("D1", input.getValue("discussionId").jsonPrimitive.content)
            assertEquals("C1", input.getValue("replyToId").jsonPrimitive.content)
            server.enqueue(MockResponse().setBody("""{"data":{"node":{"replies":{"nodes":[{"id":"R1","body":"hello","author":null,"isAnswer":false}],"pageInfo":{"hasNextPage":true,"endCursor":"cursor2"}}}}}"""))
            val replies = repository.replies("C1", "cursor1").getOrThrow()
            assertEquals("cursor2", replies.nextCursor)
            assertEquals("hello", replies.items.single().body)
            assertNull(replies.items.single().author)
            val variables = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject
            assertEquals("C1", variables.getValue("id").jsonPrimitive.content)
            assertEquals("cursor1", variables.getValue("cursor").jsonPrimitive.content)
        }
    }

    @Test fun editingUsesDiscussionIdAndMapsUpdatedContent() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            assertTrue(repository.edit("D1", " ", "text").isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"data":{"updateDiscussion":{"discussion":{"id":"D1","number":1,"title":"Edited","body":"New text","url":"https://github.com/o/r/discussions/1","author":null,"category":{"id":"C1","name":"General","isAnswerable":false},"comments":{"totalCount":0},"answer":null,"viewerCanUpdate":true}}}}"""))
            val result = repository.edit("D1", " Edited ", "New text").getOrThrow()
            assertEquals("Edited", result.title)
            assertEquals("New text", result.body)
            assertTrue(result.canEdit)
            val input = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject.getValue("input").jsonObject
            assertEquals("D1", input.getValue("discussionId").jsonPrimitive.content)
            assertEquals("Edited", input.getValue("title").jsonPrimitive.content)
        }
    }

    @Test fun detailUsesRepositoryAndNumberAndRejectsMissingDiscussion() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            assertTrue(repository.detail("a", "b", 0).isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"data":{"repository":{"discussion":null}}}"""))
            assertTrue(repository.detail("a", "b", 7).isFailure)
            val variables = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject
            assertEquals("a", variables.getValue("owner").jsonPrimitive.content)
            assertEquals("b", variables.getValue("name").jsonPrimitive.content)
            assertEquals(7, variables.getValue("number").jsonPrimitive.int)
        }
    }

    @Test fun answerMutationsUseCommentIdAndRejectGraphqlFailure() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            for (answer in listOf(true, false)) {
                val operation = if (answer) "markDiscussionCommentAsAnswer" else "unmarkDiscussionCommentAsAnswer"
                server.enqueue(MockResponse().setBody("""{"data":{"$operation":{"discussion":{"id":"D1"}}}}"""))
                assertTrue(repository.markAnswer("C1", answer).isSuccess)
                val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                assertTrue(body.getValue("query").jsonPrimitive.content.contains(operation))
                assertEquals("C1", body.getValue("variables").jsonObject.getValue("input").jsonObject.getValue("id").jsonPrimitive.content)
            }
            server.enqueue(MockResponse().setBody("""{"errors":[{"message":"forbidden"}]}"""))
            assertTrue(repository.markAnswer("C1", true).isFailure)
        }
    }

    @Test fun commentsMapAnswersDeletedAuthorsAndCursor() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"data":{"node":{"comments":{"nodes":[{"id":"C1","body":"Answer text","author":null,"isAnswer":true}],"pageInfo":{"hasNextPage":true,"endCursor":"next-comment"}}}}}"""))
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            val page = repository.comments("D1", "previous-comment").getOrThrow()
            assertEquals("next-comment", page.nextCursor)
            assertTrue(page.items.single().isAnswer)
            assertNull(page.items.single().author)
            assertEquals("Answer text", page.items.single().body)
            val variables = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject
            assertEquals("D1", variables.getValue("id").jsonPrimitive.content)
            assertEquals("previous-comment", variables.getValue("cursor").jsonPrimitive.content)
        }
    }

    @Test fun listMapsCursorAndDeletedAuthor() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"data":{"repository":{"id":"R1","discussions":{"nodes":[{"id":"D1","number":3,"title":"Help","body":"Text","url":"https://github.com/a/b/discussions/3","author":null,"category":{"id":"C1","name":"Q&A","isAnswerable":true},"comments":{"totalCount":2},"answer":{"id":"A1"}}],"pageInfo":{"hasNextPage":true,"endCursor":"next"}}}}}"""))
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            val page = repository.list("a", "b", "previous").getOrThrow()
            assertEquals("next", page.nextCursor)
            assertNull(page.items.single().author)
            assertTrue(page.items.single().answered)
            val request = server.takeRequest()
            val input = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("previous", input.getValue("variables").jsonObject.getValue("cursor").jsonPrimitive.content)
            assertTrue(request.getHeader("Authorization")!!.startsWith("Bearer "))
        }
    }

    @Test fun graphqlErrorsDoNotBecomeSuccessAndInvalidDraftDoesNotSend() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            assertTrue(repository.create("R1", "C1", " ", "body").isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"data":null,"errors":[{"message":"sensitive detail"}]}"""))
            val result = repository.reply("D1", "reply")
            assertTrue(result.exceptionOrNull() is GithubDiscussionException)
            assertFalse(result.exceptionOrNull().toString().contains("sensitive detail"))
        }
    }
}
