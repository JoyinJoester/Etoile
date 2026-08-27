package takagi.ru.monica.steam.itad.domain

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class ItadMoney(
    val amount: Double,
    val amountInt: Long,
    val currency: String
)

@Serializable
data class ItadHistoricalLow(
    val gameId: String,
    val shopId: Int,
    val shopName: String,
    val price: ItadMoney,
    val regular: ItadMoney,
    val discountPercent: Int,
    val timestamp: String,
    val sourceUrl: String,
    val fetchedAtMillis: Long
)

enum class ItadHistoryLowCompatibility {
    COMPATIBLE,
    CURRENCY_MISMATCH,
    CURRENT_STEAM_PRICE_IS_LOWER
}

internal fun resolveItadHistoryLowCompatibility(
    historicalLow: ItadHistoricalLow,
    expectedCurrency: String?,
    currentSteamPriceMinor: Long?
): ItadHistoryLowCompatibility {
    val normalizedCurrency = expectedCurrency.orEmpty().trim().uppercase(Locale.ROOT)
    if (normalizedCurrency.isNotEmpty() &&
        (historicalLow.price.currency.uppercase(Locale.ROOT) != normalizedCurrency ||
            historicalLow.regular.currency.uppercase(Locale.ROOT) != normalizedCurrency)
    ) {
        return ItadHistoryLowCompatibility.CURRENCY_MISMATCH
    }
    if (currentSteamPriceMinor != null &&
        currentSteamPriceMinor >= 0L &&
        historicalLow.price.amountInt > currentSteamPriceMinor
    ) {
        return ItadHistoryLowCompatibility.CURRENT_STEAM_PRICE_IS_LOWER
    }
    return ItadHistoryLowCompatibility.COMPATIBLE
}

enum class ItadHistoryLowFailureKind {
    API_KEY_MISSING,
    CREDENTIAL_STORAGE,
    GAME_NOT_MAPPED,
    UNAUTHORIZED,
    RATE_LIMITED,
    NETWORK,
    SERVICE,
    INVALID_RESPONSE
}

sealed interface ItadHistoryLowLoadResult {
    data class Success(
        val historicalLow: ItadHistoricalLow,
        val fromCache: Boolean,
        val stale: Boolean = false
    ) : ItadHistoryLowLoadResult

    data class Failure(
        val kind: ItadHistoryLowFailureKind,
        val retryAfterEpochMillis: Long? = null
    ) : ItadHistoryLowLoadResult
}

object ItadCountryPolicy {
    fun normalize(countryCode: String?, fallbackCountryCode: String? = null): String {
        return normalizeCandidate(countryCode)
            ?: normalizeCandidate(fallbackCountryCode)
            ?: "US"
    }

    private fun normalizeCandidate(value: String?): String? {
        val normalized = value.orEmpty().trim().uppercase(Locale.ROOT)
        if (normalized.length != 2 || normalized.any { !it.isLetter() }) return null
        return when (normalized) {
            "UK" -> "GB"
            else -> normalized
        }
    }
}
