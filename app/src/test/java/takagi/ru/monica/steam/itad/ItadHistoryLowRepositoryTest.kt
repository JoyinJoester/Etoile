package takagi.ru.monica.steam.itad

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.itad.data.ItadApiGateway
import takagi.ru.monica.steam.itad.data.ItadApiKeyProvider
import takagi.ru.monica.steam.itad.data.ItadApiResult
import takagi.ru.monica.steam.itad.data.ItadCacheGateway
import takagi.ru.monica.steam.itad.data.ItadCachedValue
import takagi.ru.monica.steam.itad.data.ItadHistoryLowRecord
import takagi.ru.monica.steam.itad.data.ItadHistoryLowRepository
import takagi.ru.monica.steam.itad.domain.ItadHistoricalLow
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowFailureKind
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowLoadResult
import takagi.ru.monica.steam.itad.domain.ItadMoney

class ItadHistoryLowRepositoryTest {
    private val now = 1_700_000_000_000L
    private val gameId = "018d937f-012f-73b8-ab2c-898516969e6a"
    private val lowRecord = ItadHistoryLowRecord(
        gameId = gameId,
        shopId = 61,
        shopName = "Steam",
        price = ItadMoney(9.99, 999, "CNY"),
        regular = ItadMoney(99.99, 9999, "CNY"),
        discountPercent = 90,
        timestamp = "2026-01-01T00:00:00Z"
    )

    @Test
    fun firstLoadMapsAppFetchesHistoryAndCachesResult() = runTest {
        val cache = MemoryCache()
        val api = FakeApi(gameId, lowRecord)
        val repository = repository(api, cache)

        val result = repository.load(620, "cn") as ItadHistoryLowLoadResult.Success

        assertFalse(result.fromCache)
        assertEquals("CNY", result.historicalLow.price.currency)
        assertEquals("https://isthereanydeal.com/game/portal-2/", result.historicalLow.sourceUrl)
        assertEquals("CN", api.requestedCountry)
        assertEquals("test-api-key", api.requestedApiKey)
        assertEquals(1, api.lookupCalls)
        assertTrue(cache.history.containsKey("CN:620"))
    }

    @Test
    fun freshCacheAvoidsNetworkCalls() = runTest {
        val cached = historicalLow()
        val cache = MemoryCache().apply {
            history["CN:620"] = ItadCachedValue(cached, now + 10_000L)
        }
        val api = FakeApi(gameId, lowRecord)
        val repository = repository(api, cache)

        val result = repository.load(620, "CN") as ItadHistoryLowLoadResult.Success

        assertTrue(result.fromCache)
        assertFalse(result.stale)
        assertEquals(0, api.lookupCalls)
    }

    @Test
    fun rateLimitStoresWindowAndReturnsOfficialRetryDeadline() = runTest {
        val cache = MemoryCache()
        val api = FakeApi(gameId, lowRecord).apply {
            historyResult = ItadApiResult.RateLimited(now + 120_000L)
        }
        val repository = repository(api, cache)

        val result = repository.load(620, "CN") as ItadHistoryLowLoadResult.Failure

        assertEquals(ItadHistoryLowFailureKind.RATE_LIMITED, result.kind)
        assertEquals(now + 120_000L, result.retryAfterEpochMillis)
        assertTrue(cache.retryAfter.values.contains(now + 120_000L))
    }

    @Test
    fun staleCacheRemainsAvailableDuringNetworkFailure() = runTest {
        val cached = historicalLow()
        val cache = MemoryCache().apply {
            history["CN:620"] = ItadCachedValue(cached, now - 1L)
        }
        val api = FakeApi(gameId, lowRecord).apply {
            lookupResult = ItadApiResult.NetworkFailure
        }
        val repository = repository(api, cache)

        val result = repository.load(620, "CN") as ItadHistoryLowLoadResult.Success

        assertTrue(result.fromCache)
        assertTrue(result.stale)
        assertEquals(cached, result.historicalLow)
    }

    @Test
    fun unauthorizedKeyIsReportedEvenWhenStaleDataExists() = runTest {
        val cache = MemoryCache().apply {
            history["CN:620"] = ItadCachedValue(historicalLow(), now - 1L)
        }
        val api = FakeApi(gameId, lowRecord).apply {
            historyResult = ItadApiResult.HttpFailure(401)
        }
        val repository = repository(api, cache)

        val result = repository.load(620, "CN") as ItadHistoryLowLoadResult.Failure

        assertEquals(ItadHistoryLowFailureKind.UNAUTHORIZED, result.kind)
    }

    private fun repository(api: FakeApi, cache: MemoryCache) = ItadHistoryLowRepository(
        credentialStore = ItadApiKeyProvider { "test-api-key" },
        api = api,
        cache = cache,
        clock = { now },
        localeCountry = { "US" }
    )

    private fun historicalLow() = ItadHistoricalLow(
        gameId = gameId,
        shopId = 61,
        shopName = "Steam",
        price = lowRecord.price,
        regular = lowRecord.regular,
        discountPercent = 90,
        timestamp = lowRecord.timestamp,
        sourceUrl = "https://isthereanydeal.com/game/portal-2/",
        fetchedAtMillis = now - 100_000L
    )

    private class FakeApi(
        private val gameId: String,
        private val lowRecord: ItadHistoryLowRecord
    ) : ItadApiGateway {
        var lookupCalls = 0
        var requestedCountry: String? = null
        var requestedApiKey: String? = null
        var lookupResult: ItadApiResult<String?> = ItadApiResult.Success(gameId)
        var historyResult: ItadApiResult<ItadHistoryLowRecord> =
            ItadApiResult.Success(lowRecord)

        override fun lookupSteamAppId(appId: Int): ItadApiResult<String?> {
            lookupCalls++
            return lookupResult
        }

        override fun loadHistoryLow(
            gameId: String,
            countryCode: String,
            apiKey: String
        ): ItadApiResult<ItadHistoryLowRecord> {
            requestedCountry = countryCode
            requestedApiKey = apiKey
            return historyResult
        }

        override fun loadGameUrl(gameId: String, apiKey: String): ItadApiResult<String> =
            ItadApiResult.Success("https://isthereanydeal.com/game/portal-2/")
    }

    private class MemoryCache : ItadCacheGateway {
        val gameIds = mutableMapOf<Int, ItadCachedValue<String>>()
        val gameUrls = mutableMapOf<String, ItadCachedValue<String>>()
        val history = mutableMapOf<String, ItadCachedValue<ItadHistoricalLow>>()
        val retryAfter = mutableMapOf<String, Long>()

        override fun readGameId(appId: Int) = gameIds[appId]
        override fun writeGameId(appId: Int, gameId: String, expiresAtMillis: Long) {
            gameIds[appId] = ItadCachedValue(gameId, expiresAtMillis)
        }
        override fun readGameUrl(gameId: String) = gameUrls[gameId]
        override fun writeGameUrl(gameId: String, url: String, expiresAtMillis: Long) {
            gameUrls[gameId] = ItadCachedValue(url, expiresAtMillis)
        }
        override fun readHistoryLow(appId: Int, countryCode: String) =
            history["$countryCode:$appId"]
        override fun writeHistoryLow(
            appId: Int,
            countryCode: String,
            value: ItadHistoricalLow,
            expiresAtMillis: Long
        ) {
            history["$countryCode:$appId"] = ItadCachedValue(value, expiresAtMillis)
        }
        override fun readRetryAfter(keyFingerprint: String) = retryAfter[keyFingerprint]
        override fun writeRetryAfter(keyFingerprint: String, retryAfterEpochMillis: Long) {
            retryAfter[keyFingerprint] = retryAfterEpochMillis
        }
    }
}
