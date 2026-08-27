package takagi.ru.monica.steam.community.eligibility.domain

import kotlin.math.absoluteValue
import kotlinx.serialization.Serializable

@Serializable
enum class SteamCommunityProgressSource {
    NONE,
    STEAM_SUPPORT,
    TRANSACTION_HISTORY
}

internal enum class SteamCommunityTransactionKind {
    STORE_PURCHASE,
    GIFT_PURCHASE,
    WALLET_CREDIT,
    REFUND,
    MARKET,
    OTHER
}

internal enum class SteamCommunityPaymentSource {
    EXTERNAL,
    STEAM_WALLET,
    UNKNOWN
}

internal data class SteamCommunityTransaction(
    val kind: SteamCommunityTransactionKind,
    val amountMinor: Int,
    val currencyCode: String,
    val paymentSource: SteamCommunityPaymentSource = SteamCommunityPaymentSource.UNKNOWN
)

internal data class SteamCommunityTransactionEstimate(
    val lowerBoundUsdCents: Int,
    val upperBoundUsdCents: Int
) {
    init {
        require(lowerBoundUsdCents >= 0)
        require(upperBoundUsdCents >= lowerBoundUsdCents)
    }

    fun mayHaveReached(thresholdUsdCents: Int): Boolean =
        thresholdUsdCents > 0 && upperBoundUsdCents >= thresholdUsdCents
}

internal data class SteamCommunitySpendResolution(
    val progressSource: SteamCommunityProgressSource,
    val spentUsdCents: Int?,
    val estimatedSpentUpperUsdCents: Int?,
    val remainingUsdCents: Int,
    val exactProgress: Boolean
)

internal fun estimateCommunitySpend(
    transactions: List<SteamCommunityTransaction>,
    unitsPerCny: Map<String, Double>
): SteamCommunityTransactionEstimate? {
    var lowerBound = 0L
    var upperBound = 0L
    var considered = false
    transactions.forEach { transaction ->
        val usdCents = SteamCommunityUnlockCalculator.usdMinorFromLocal(
            localMinor = transaction.amountMinor.absoluteValue,
            currencyCode = transaction.currencyCode,
            unitsPerCny = unitsPerCny
        ) ?: return@forEach
        val amount = usdCents.coerceAtLeast(0L)
        when (transaction.kind) {
            SteamCommunityTransactionKind.STORE_PURCHASE,
            SteamCommunityTransactionKind.GIFT_PURCHASE -> {
                upperBound += amount
                if (transaction.paymentSource == SteamCommunityPaymentSource.EXTERNAL) {
                    lowerBound += amount
                }
                considered = true
            }
            SteamCommunityTransactionKind.WALLET_CREDIT -> {
                lowerBound += amount
                upperBound += amount
                considered = true
            }
            SteamCommunityTransactionKind.REFUND -> {
                lowerBound -= amount
                upperBound -= amount
                considered = true
            }
            SteamCommunityTransactionKind.MARKET,
            SteamCommunityTransactionKind.OTHER -> Unit
        }
    }
    if (!considered) return null
    val safeLower = lowerBound.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val safeUpper = upperBound.coerceAtLeast(safeLower.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return SteamCommunityTransactionEstimate(
        lowerBoundUsdCents = safeLower,
        upperBoundUsdCents = safeUpper
    )
}

internal fun resolveCommunitySpendProgress(
    status: SteamCommunityRestrictionStatus,
    thresholdUsdCents: Int,
    support: SteamLimitedAccountSupportProgress?,
    transactionEstimate: SteamCommunityTransactionEstimate?
): SteamCommunitySpendResolution {
    val threshold = thresholdUsdCents.coerceAtLeast(0)
    if (support?.hasExactProgress == true) {
        val remaining = support.remainingUsdCents
            ?: support.spentUsdCents?.let { (threshold - it).coerceAtLeast(0) }
            ?: threshold
        val spent = support.spentUsdCents
            ?: (threshold - remaining).coerceAtLeast(0)
        return SteamCommunitySpendResolution(
            progressSource = SteamCommunityProgressSource.STEAM_SUPPORT,
            spentUsdCents = spent,
            estimatedSpentUpperUsdCents = spent,
            remainingUsdCents = if (status == SteamCommunityRestrictionStatus.UNRESTRICTED) {
                0
            } else {
                remaining.coerceAtLeast(0)
            },
            exactProgress = true
        )
    }
    if (status != SteamCommunityRestrictionStatus.UNRESTRICTED && transactionEstimate != null) {
        return SteamCommunitySpendResolution(
            progressSource = SteamCommunityProgressSource.TRANSACTION_HISTORY,
            spentUsdCents = transactionEstimate.lowerBoundUsdCents,
            estimatedSpentUpperUsdCents = transactionEstimate.upperBoundUsdCents,
            remainingUsdCents = (threshold - transactionEstimate.lowerBoundUsdCents)
                .coerceAtLeast(0),
            exactProgress = false
        )
    }
    return SteamCommunitySpendResolution(
        progressSource = SteamCommunityProgressSource.NONE,
        spentUsdCents = null,
        estimatedSpentUpperUsdCents = null,
        remainingUsdCents = if (status == SteamCommunityRestrictionStatus.UNRESTRICTED) 0 else threshold,
        exactProgress = false
    )
}
