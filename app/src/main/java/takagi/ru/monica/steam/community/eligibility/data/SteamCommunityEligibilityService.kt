package takagi.ru.monica.steam.community.eligibility.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import takagi.ru.monica.steam.community.eligibility.domain.DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
import takagi.ru.monica.steam.community.eligibility.domain.CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityEligibilityGateway
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityBudgetGame
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockCalculator
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockSource
import takagi.ru.monica.steam.community.eligibility.domain.estimateCommunitySpend
import takagi.ru.monica.steam.community.eligibility.domain.resolveCommunitySpendProgress
import takagi.ru.monica.steam.community.eligibility.domain.steamCurrencyForCountry
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.library.SteamCurrencyExchangeService
import takagi.ru.monica.steam.store.data.SteamStoreService

internal class SteamCommunityEligibilityService(
    private val accountInfoService: SteamCommunityAccountInfoService =
        SteamCommunityAccountInfoService(),
    private val supportService: SteamLimitedAccountSupportService =
        SteamLimitedAccountSupportService(),
    private val purchaseHistoryService: SteamAccountPurchaseHistoryService =
        SteamAccountPurchaseHistoryService(),
    private val storeService: SteamStoreService = SteamStoreService(),
    private val exchangeService: SteamCurrencyExchangeService = SteamCurrencyExchangeService(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamCommunityEligibilityGateway {
    override suspend fun fetch(account: SteamAccount): SteamCommunityUnlockProgress =
        coroutineScope {
        val accountInfoRequest = async { accountInfoService.fetch(account) }
        val supportRequest = async(Dispatchers.IO) {
            runCatching { supportService.fetch(account) }
                .onFailure { error ->
                    SteamDiagLogger.append(
                        "community_eligibility support_failed type=${error.javaClass.simpleName}"
                    )
                }
                .getOrNull()
        }
        val countryRequest = async(Dispatchers.IO) {
            runCatching { storeService.accountCountryCode(account) }.getOrNull()
        }
        val ratesRequest = async(Dispatchers.IO) {
            runCatching { exchangeService.fetchCnyRates() }.getOrNull()
        }
        val wishlistRequest = async(Dispatchers.IO) {
            runCatching {
                storeService.wishlist(
                    steamId = account.steamId,
                    steamLoginSecure = account.steamLoginSecure,
                    accessToken = account.accessToken
                ).mapTo(linkedSetOf()) { it.appId }
            }.getOrDefault(emptySet())
        }
        val accountInfo = accountInfoRequest.await()
        val support = supportRequest.await()
        val countryCode = countryRequest.await()?.takeIf(String::isNotBlank)
            ?: accountInfo?.countryCode.orEmpty()
        val currencyCode = steamCurrencyForCountry(countryCode)
        val supportProvesThresholdReached = support?.let { progress ->
            val spent = progress.spentUsdCents
            val threshold = progress.thresholdUsdCents
            spent != null && threshold != null && spent >= threshold
        } == true
        val status = resolveCommunityRestrictionStatus(
            supportLimited = support?.limited,
            accountFlagsLimited = accountInfo?.limited,
            supportProvesThresholdReached = supportProvesThresholdReached
        )
        val trustedSupport = support?.takeIf { progress ->
            when {
                accountInfo?.limited == true && progress.limited == false -> false
                progress.limited == true -> true
                status == SteamCommunityRestrictionStatus.UNRESTRICTED ->
                    accountInfo?.limited == false || supportProvesThresholdReached
                else -> false
            }
        }
        val thresholdUsd = trustedSupport?.thresholdUsdCents
            ?: DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
        val rates = ratesRequest.await()
        val transactionEstimate = if (
            status != SteamCommunityRestrictionStatus.UNRESTRICTED &&
            trustedSupport?.hasExactProgress != true
        ) {
            runCatching {
                purchaseHistoryService.fetch(account, currencyCode)
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "community_eligibility history_failed type=${error.javaClass.simpleName}"
                )
            }.getOrNull()?.let { transactions ->
                estimateCommunitySpend(
                    transactions = transactions,
                    unitsPerCny = rates?.unitsPerCny.orEmpty()
                )
            }
        } else {
            null
        }
        val spendProgress = resolveCommunitySpendProgress(
            status = status,
            thresholdUsdCents = thresholdUsd,
            support = trustedSupport,
            transactionEstimate = transactionEstimate
        )
        val remainingUsd = spendProgress.remainingUsdCents
        val localThreshold = rates?.let {
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                thresholdUsd,
                currencyCode,
                it.unitsPerCny
            )
        }
        val localRemaining = rates?.let {
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                remainingUsd,
                currencyCode,
                it.unitsPerCny
            )
        }
        val wishlistAppIds = wishlistRequest.await()
        val suggestions = if (
            status != SteamCommunityRestrictionStatus.UNRESTRICTED &&
            !countryCode.isBlank() &&
            localRemaining != null &&
            localRemaining in 1..Int.MAX_VALUE.toLong()
        ) {
            runCatching {
                storeService.budgetSuggestions(
                    targetMinor = localRemaining.toInt(),
                    countryCode = countryCode,
                    steamLoginSecure = account.steamLoginSecure,
                    wishlistAppIds = wishlistAppIds
                ).mapNotNull { item ->
                    val price = item.finalPriceCents ?: return@mapNotNull null
                    SteamCommunityBudgetGame(
                        appId = item.appId,
                        name = item.name,
                        imageUrl = item.imageUrl.ifBlank { item.headerImageUrl },
                        currency = item.currency,
                        finalPriceMinor = price,
                        originalPriceMinor = item.initialPriceCents,
                        discountPercent = item.discountPercent,
                        inWishlist = item.appId in wishlistAppIds
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        SteamCommunityUnlockProgress(
            status = status,
            source = when {
                trustedSupport != null ->
                    SteamCommunityUnlockSource.STEAM_SUPPORT
                accountInfo?.limited == true -> SteamCommunityUnlockSource.STEAM_ACCOUNT_FLAGS
                else -> SteamCommunityUnlockSource.ESTIMATE
            },
            accountCountryCode = countryCode,
            accountCurrencyCode = currencyCode,
            thresholdUsdCents = thresholdUsd,
            spentUsdCents = spendProgress.spentUsdCents,
            estimatedSpentUpperUsdCents = spendProgress.estimatedSpentUpperUsdCents,
            remainingUsdCents = remainingUsd,
            localThresholdMinor = localThreshold,
            localRemainingMinor = localRemaining,
            exchangeRateFetchedAt = rates?.fetchedAt,
            exactProgress = spendProgress.exactProgress,
            progressSource = spendProgress.progressSource,
            evidenceRevision = CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION,
            suggestedGames = suggestions,
            fetchedAt = nowMillis()
        )
    }
}

internal fun resolveCommunityRestrictionStatus(
    supportLimited: Boolean?,
    accountFlagsLimited: Boolean?,
    supportProvesThresholdReached: Boolean = false
): SteamCommunityRestrictionStatus = when {
    accountFlagsLimited == true -> SteamCommunityRestrictionStatus.LIMITED
    supportLimited == true -> SteamCommunityRestrictionStatus.LIMITED
    supportLimited == false &&
        (accountFlagsLimited == false || supportProvesThresholdReached) ->
        SteamCommunityRestrictionStatus.UNRESTRICTED
    else -> SteamCommunityRestrictionStatus.UNKNOWN
}
