package takagi.ru.monica.steam.itad.data

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.network.SteamHttpClientProvider

internal sealed interface ItadApiResult<out T> {
    data class Success<T>(val value: T) : ItadApiResult<T>
    data class RateLimited(val retryAfterEpochMillis: Long) : ItadApiResult<Nothing>
    data class HttpFailure(val statusCode: Int) : ItadApiResult<Nothing>
    data object NetworkFailure : ItadApiResult<Nothing>
    data object InvalidResponse : ItadApiResult<Nothing>
}

internal data class ItadHistoryLowRecord(
    val gameId: String,
    val shopId: Int,
    val shopName: String,
    val price: ItadMoney,
    val regular: ItadMoney,
    val discountPercent: Int,
    val timestamp: String
)

internal interface ItadApiGateway {
    fun lookupSteamAppId(appId: Int): ItadApiResult<String?>
    fun loadHistoryLow(
        gameId: String,
        countryCode: String,
        apiKey: String
    ): ItadApiResult<ItadHistoryLowRecord>
    fun loadGameUrl(gameId: String, apiKey: String): ItadApiResult<String>
}

internal class ItadApiClient(
    private val client: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis
) : ItadApiGateway {
    override fun lookupSteamAppId(appId: Int): ItadApiResult<String?> {
        if (appId <= 0) return ItadApiResult.InvalidResponse
        val shopIdentifier = "app/$appId"
        val request = Request.Builder()
            .url(endpoint("lookup/id/shop/61/v1"))
            .post(json.encodeToString(listOf(shopIdentifier)).toRequestBody(JSON_MEDIA_TYPE))
            .commonHeaders()
            .build()
        return execute(request) { payload ->
            json.decodeFromString<Map<String, String?>>(payload)[shopIdentifier]
        }
    }

    override fun loadHistoryLow(
        gameId: String,
        countryCode: String,
        apiKey: String
    ): ItadApiResult<ItadHistoryLowRecord> {
        val requestUrl = endpoint("games/historylow/v1").newBuilder()
            .addQueryParameter("country", countryCode)
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .post(json.encodeToString(listOf(gameId)).toRequestBody(JSON_MEDIA_TYPE))
            .commonHeaders()
            .header(API_KEY_HEADER, apiKey)
            .build()
        return when (val response = execute<List<HistoryLowResponse>>(request) { payload ->
            json.decodeFromString(payload)
        }) {
            is ItadApiResult.Success -> {
                val record = response.value.firstOrNull { it.id == gameId }
                    ?: return ItadApiResult.InvalidResponse
                ItadApiResult.Success(
                    ItadHistoryLowRecord(
                        gameId = record.id,
                        shopId = record.low.shop.id,
                        shopName = record.low.shop.name,
                        price = record.low.price.toDomain(),
                        regular = record.low.regular.toDomain(),
                        discountPercent = record.low.cut,
                        timestamp = record.low.timestamp
                    )
                )
            }
            is ItadApiResult.RateLimited ->
                ItadApiResult.RateLimited(response.retryAfterEpochMillis)
            is ItadApiResult.HttpFailure ->
                ItadApiResult.HttpFailure(response.statusCode)
            ItadApiResult.NetworkFailure -> ItadApiResult.NetworkFailure
            ItadApiResult.InvalidResponse -> ItadApiResult.InvalidResponse
        }
    }

    override fun loadGameUrl(gameId: String, apiKey: String): ItadApiResult<String> {
        val requestUrl = endpoint("games/info/v2").newBuilder()
            .addQueryParameter("id", gameId)
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .commonHeaders()
            .header(API_KEY_HEADER, apiKey)
            .build()
        return execute(request) { payload ->
            val officialUrl = json.decodeFromString<GameInfoResponse>(payload).urls.game
            val parsed = officialUrl.toHttpUrlOrNull()
            require(
                parsed?.scheme == "https" &&
                    (parsed.host == "isthereanydeal.com" ||
                        parsed.host.endsWith(".isthereanydeal.com"))
            )
            officialUrl
        }
    }

    private inline fun <T> execute(
        request: Request,
        parse: (String) -> T
    ): ItadApiResult<T> {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    return ItadApiResult.RateLimited(
                        parseRetryAfter(response.header("Retry-After"))
                    )
                }
                if (!response.isSuccessful) {
                    return ItadApiResult.HttpFailure(response.code)
                }
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return ItadApiResult.InvalidResponse
                runCatching { parse(payload) }
                    .fold(
                        onSuccess = { ItadApiResult.Success(it) },
                        onFailure = { ItadApiResult.InvalidResponse }
                    )
            }
        } catch (_: IOException) {
            ItadApiResult.NetworkFailure
        } catch (_: IllegalArgumentException) {
            ItadApiResult.InvalidResponse
        }
    }

    private fun parseRetryAfter(rawValue: String?): Long {
        val now = clock()
        rawValue?.trim()?.toLongOrNull()?.let { seconds ->
            return now + seconds.coerceAtLeast(0L) * 1_000L
        }
        val parsedDate = runCatching {
            ZonedDateTime.parse(rawValue, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
        return parsedDate?.coerceAtLeast(now) ?: now + DEFAULT_RETRY_AFTER_MILLIS
    }

    private fun endpoint(path: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegments(path)
        .build()

    private fun Request.Builder.commonHeaders(): Request.Builder =
        header("Accept", "application/json")
            .header("User-Agent", "Etoile/1.0 ITAD-integration")

    @Serializable
    private data class HistoryLowResponse(
        val id: String,
        val low: HistoryLow
    )

    @Serializable
    private data class HistoryLow(
        val shop: Shop,
        val price: Price,
        val regular: Price,
        val cut: Int,
        val timestamp: String
    )

    @Serializable
    private data class Shop(val id: Int, val name: String)

    @Serializable
    private data class Price(
        val amount: Double,
        val amountInt: Long,
        val currency: String
    ) {
        fun toDomain(): ItadMoney = ItadMoney(amount, amountInt, currency)
    }

    @Serializable
    private data class GameInfoResponse(val urls: GameUrls)

    @Serializable
    private data class GameUrls(val game: String)

    private companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://api.isthereanydeal.com/".toHttpUrl()
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val API_KEY_HEADER = "ITAD-API-Key"
        const val DEFAULT_RETRY_AFTER_MILLIS = 60_000L
    }
}
