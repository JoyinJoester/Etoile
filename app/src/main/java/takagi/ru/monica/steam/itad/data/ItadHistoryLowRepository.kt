package takagi.ru.monica.steam.itad.data

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.itad.domain.ItadApiKeyPolicy
import takagi.ru.monica.steam.itad.domain.ItadCountryPolicy
import takagi.ru.monica.steam.itad.domain.ItadHistoricalLow
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowFailureKind
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowLoadResult

class ItadHistoryLowRepository internal constructor(
    private val credentialStore: ItadApiKeyProvider,
    private val api: ItadApiGateway,
    private val cache: ItadCacheGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val localeCountry: () -> String = { Locale.getDefault().country }
) {
    constructor(context: Context) : this(
        credentialStore = ItadCredentialStore(context.applicationContext),
        api = ItadApiClient(),
        cache = ItadHistoryLowCache(context.applicationContext)
    )

    suspend fun load(
        appId: Int,
        countryCode: String?,
        force: Boolean = false
    ): ItadHistoryLowLoadResult = withContext(Dispatchers.IO) {
        loadBlocking(appId, countryCode, force)
    }

    private fun loadBlocking(
        appId: Int,
        countryCode: String?,
        force: Boolean
    ): ItadHistoryLowLoadResult {
        if (appId <= 0) return failure(ItadHistoryLowFailureKind.GAME_NOT_MAPPED)
        val apiKey = runCatching { credentialStore.readApiKey() }
            .getOrElse { return failure(ItadHistoryLowFailureKind.CREDENTIAL_STORAGE) }
            ?.let(ItadApiKeyPolicy::validate)
            ?.normalizedKey
            ?: return failure(ItadHistoryLowFailureKind.API_KEY_MISSING)
        val normalizedCountry = ItadCountryPolicy.normalize(countryCode, localeCountry())
        val now = clock()
        val cachedHistory = cache.readHistoryLow(appId, normalizedCountry)
        if (!force && cachedHistory?.isFresh(now) == true) {
            return ItadHistoryLowLoadResult.Success(
                historicalLow = cachedHistory.value,
                fromCache = true
            )
        }

        val keyFingerprint = apiKeyFingerprint(apiKey)
        val retryAfter = cache.readRetryAfter(keyFingerprint)
        if (retryAfter != null && retryAfter > now) {
            return cachedHistory?.let {
                ItadHistoryLowLoadResult.Success(it.value, fromCache = true, stale = true)
            } ?: ItadHistoryLowLoadResult.Failure(
                ItadHistoryLowFailureKind.RATE_LIMITED,
                retryAfterEpochMillis = retryAfter
            )
        }

        val gameId = cache.readGameId(appId)
            ?.takeIf { it.isFresh(now) }
            ?.value
            ?: when (val lookup = api.lookupSteamAppId(appId)) {
                is ItadApiResult.Success -> {
                    val mapped = lookup.value
                        ?: return cachedOrFailure(
                            cachedHistory,
                            ItadHistoryLowFailureKind.GAME_NOT_MAPPED
                        )
                    cache.writeGameId(appId, mapped, now + GAME_ID_CACHE_MILLIS)
                    mapped
                }
                else -> return handleApiFailure(lookup, keyFingerprint, cachedHistory)
            }

        val historyRecord = when (
            val history = api.loadHistoryLow(gameId, normalizedCountry, apiKey)
        ) {
            is ItadApiResult.Success -> history.value
            else -> return handleApiFailure(history, keyFingerprint, cachedHistory)
        }

        val cachedUrl = cache.readGameUrl(gameId)
            ?.takeIf { it.isFresh(now) }
            ?.value
        val sourceUrl = cachedUrl ?: when (val info = api.loadGameUrl(gameId, apiKey)) {
            is ItadApiResult.Success -> info.value.also { url ->
                cache.writeGameUrl(gameId, url, now + GAME_URL_CACHE_MILLIS)
            }
            is ItadApiResult.RateLimited -> {
                cache.writeRetryAfter(keyFingerprint, info.retryAfterEpochMillis)
                ITAD_HOME_URL
            }
            else -> ITAD_HOME_URL
        }
        val historicalLow = ItadHistoricalLow(
            gameId = historyRecord.gameId,
            shopId = historyRecord.shopId,
            shopName = historyRecord.shopName,
            price = historyRecord.price,
            regular = historyRecord.regular,
            discountPercent = historyRecord.discountPercent,
            timestamp = historyRecord.timestamp,
            sourceUrl = sourceUrl,
            fetchedAtMillis = now
        )
        cache.writeHistoryLow(
            appId = appId,
            countryCode = normalizedCountry,
            value = historicalLow,
            expiresAtMillis = now + HISTORY_LOW_CACHE_MILLIS
        )
        return ItadHistoryLowLoadResult.Success(historicalLow, fromCache = false)
    }

    private fun handleApiFailure(
        result: ItadApiResult<*>,
        keyFingerprint: String,
        cachedHistory: ItadCachedValue<ItadHistoricalLow>?
    ): ItadHistoryLowLoadResult {
        val failure = when (result) {
            is ItadApiResult.RateLimited -> {
                cache.writeRetryAfter(keyFingerprint, result.retryAfterEpochMillis)
                ItadHistoryLowLoadResult.Failure(
                    kind = ItadHistoryLowFailureKind.RATE_LIMITED,
                    retryAfterEpochMillis = result.retryAfterEpochMillis
                )
            }
            is ItadApiResult.HttpFailure -> failure(
                if (result.statusCode == 401 || result.statusCode == 403) {
                    ItadHistoryLowFailureKind.UNAUTHORIZED
                } else {
                    ItadHistoryLowFailureKind.SERVICE
                }
            )
            ItadApiResult.NetworkFailure -> failure(ItadHistoryLowFailureKind.NETWORK)
            ItadApiResult.InvalidResponse -> failure(ItadHistoryLowFailureKind.INVALID_RESPONSE)
            is ItadApiResult.Success<*> -> failure(ItadHistoryLowFailureKind.INVALID_RESPONSE)
        }
        val mayUseStaleCache = (failure as? ItadHistoryLowLoadResult.Failure)?.kind !=
            ItadHistoryLowFailureKind.UNAUTHORIZED
        return cachedHistory?.takeIf { mayUseStaleCache }?.let {
            ItadHistoryLowLoadResult.Success(it.value, fromCache = true, stale = true)
        } ?: failure
    }

    private fun cachedOrFailure(
        cachedHistory: ItadCachedValue<ItadHistoricalLow>?,
        kind: ItadHistoryLowFailureKind
    ): ItadHistoryLowLoadResult = cachedHistory?.let {
        ItadHistoryLowLoadResult.Success(it.value, fromCache = true, stale = true)
    } ?: failure(kind)

    private fun failure(kind: ItadHistoryLowFailureKind) =
        ItadHistoryLowLoadResult.Failure(kind)

    private fun apiKeyFingerprint(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val ITAD_HOME_URL = "https://isthereanydeal.com/"
        const val HISTORY_LOW_CACHE_MILLIS = 12L * 60L * 60L * 1_000L
        const val GAME_ID_CACHE_MILLIS = 180L * 24L * 60L * 60L * 1_000L
        const val GAME_URL_CACHE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }
}
