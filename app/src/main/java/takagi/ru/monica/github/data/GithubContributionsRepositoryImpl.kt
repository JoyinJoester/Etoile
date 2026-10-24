package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionDay
import takagi.ru.monica.github.domain.GithubContributionWeek
import takagi.ru.monica.github.domain.GithubContributionsRepository

/**
 * 贡献日历实现。REST v3 没有贡献日历端点，因此直接查询 GraphQL API 的
 * contributionsCollection.contributionCalendar，并解析为本地域模型。
 */
class GithubContributionsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoint: String = "https://api.github.com/graphql"
) : GithubContributionsRepository {

    override suspend fun getContributionCalendar(username: String): Result<GithubContributionCalendar> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val payload = buildJsonObject {
                    put("query", QUERY)
                    putJsonObject("variables") { put("login", username) }
                }
                val request = requests.builder(endpoint)
                    .post(
                        payload.toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                    )
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException.of(response)
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    root["errors"]?.jsonArray?.takeIf { it.isNotEmpty() }?.let { errors ->
                        throw IllegalStateException(
                            errors.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
                                ?: "GraphQL request failed"
                        )
                    }
                    val calendar = root["data"]?.jsonObject?.get("user")?.jsonObject
                        ?.get("contributionsCollection")?.jsonObject
                        ?.get("contributionCalendar")?.jsonObject
                        ?: throw IllegalStateException("Contribution calendar unavailable")
                    val weeks = calendar["weeks"]?.jsonArray?.map { weekElement ->
                        // GraphQL returns each week as an object containing a
                        // contributionDays array. Treat a missing array as a
                        // malformed response instead of attempting to cast the
                        // week object itself to an array (which made the
                        // contribution calendar fail to load every time).
                        val days = weekElement.jsonObject["contributionDays"]?.jsonArray
                            ?: throw IllegalStateException("Malformed contribution week")
                        GithubContributionWeek(
                            days = days.mapNotNull { dayElement ->
                                val day = dayElement.jsonObject
                                val dateText = day["date"]?.jsonPrimitive
                                    ?.takeIf { it !is JsonNull }
                                    ?.contentOrNull
                                if (dateText == null) {
                                    null
                                } else {
                                    GithubContributionDay(
                                        date = dateText,
                                        count = day["contributionCount"]?.jsonPrimitive?.int ?: 0,
                                        level = day["contributionLevel"]?.jsonPrimitive?.content?.toLevel() ?: 0
                                    )
                                }
                            }
                        )
                    } ?: throw IllegalStateException("Malformed contribution calendar")
                    GithubContributionCalendar(
                        weeks = weeks,
                        totalContributions = calendar["totalContributions"]!!.jsonPrimitive.int
                    )
                }
            }
        }

    private fun String.toLevel(): Int = when (this) {
        "FIRST_QUARTILE" -> 1
        "SECOND_QUARTILE" -> 2
        "THIRD_QUARTILE" -> 3
        "FOURTH_QUARTILE" -> 4
        else -> 0
    }

    private companion object {
        private const val QUERY = """
            query(${'$'}login: String!) {
              user(login: ${'$'}login) {
                contributionsCollection {
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays {
                        date
                        contributionCount
                        contributionLevel
                      }
                    }
                  }
                }
              }
            }
        """
    }
}
