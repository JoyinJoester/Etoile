package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.*
import org.junit.Before
import org.junit.Test

class GithubContributionsRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun parsesContributionDaysNestedInsideEachWeekObject() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RESPONSE))

        val calendar = GithubContributionsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            endpoint = server.url("/graphql").toString()
        ).getContributionCalendar("alice").getOrThrow()

        assertEquals(7, calendar.weeks.single().days.size)
        assertEquals("2026-09-20", calendar.weeks.single().days.first().date)
        assertEquals(3, calendar.weeks.single().days.first().count)
        assertEquals(4, calendar.weeks.single().days.first().level)
        assertEquals(3, calendar.totalContributions)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/graphql", request.path)
        assertEquals("alice", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["variables"]!!.jsonObject["login"]!!.jsonPrimitive.content)
    }

    @Test fun preservesPartialWeeksAndEmptyCalendar() = runTest {
        server.enqueue(MockResponse().setBody(RESPONSE.replace("\"2026-09-20\"", "null")))
        assertEquals(6, repository().getContributionCalendar("alice").getOrThrow().weeks.single().days.size)
        server.enqueue(MockResponse().setBody("""{"data":{"user":{"contributionsCollection":{"contributionCalendar":{"totalContributions":0,"weeks":[]}}}}}"""))
        val empty = repository().getContributionCalendar("alice").getOrThrow()
        assertTrue(empty.weeks.isEmpty())
        assertEquals(0, empty.totalContributions)
    }

    @Test fun rejectsMissingDaysInsteadOfShowingAnEmptySuccess() = runTest {
        server.enqueue(MockResponse().setBody(RESPONSE.replace("contributionDays", "unexpected")))
        assertTrue(repository().getContributionCalendar("alice").isFailure)
    }

    @Test fun reportsGraphqlErrorsAndUnknownUsers() = runTest {
        server.enqueue(MockResponse().setBody("""{"errors":[{"message":"Unavailable"}]}"""))
        assertTrue(repository().getContributionCalendar("alice").isFailure)
        server.enqueue(MockResponse().setBody("""{"data":{"user":null}}"""))
        assertTrue(repository().getContributionCalendar("alice").isFailure)
    }

    @Test fun preservesHttpFailureForRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val error = repository().getContributionCalendar("alice").exceptionOrNull()
        assertTrue(error is GithubApiException)
        assertEquals(403, (error as GithubApiException).statusCode)
    }

    private fun repository() = GithubContributionsRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()), client = OkHttpClient(),
        endpoint = server.url("/graphql").toString()
    )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val RESPONSE = """
            {
              "data": {
                "user": {
                  "contributionsCollection": {
                    "contributionCalendar": {
                      "totalContributions": 3,
                      "weeks": [{
                        "contributionDays": [
                          {"date":"2026-09-20","contributionCount":3,"contributionLevel":"FOURTH_QUARTILE"},
                          {"date":"2026-09-21","contributionCount":0,"contributionLevel":"NONE"},
                          {"date":"2026-09-22","contributionCount":0,"contributionLevel":"NONE"},
                          {"date":"2026-09-23","contributionCount":0,"contributionLevel":"NONE"},
                          {"date":"2026-09-24","contributionCount":0,"contributionLevel":"NONE"},
                          {"date":"2026-09-25","contributionCount":0,"contributionLevel":"NONE"},
                          {"date":"2026-09-26","contributionCount":0,"contributionLevel":"NONE"}
                        ]
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
