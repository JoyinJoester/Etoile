package takagi.ru.monica.steam.store.purchase.data

import java.util.concurrent.ConcurrentHashMap
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.family.SteamFamilyLibraryFetch
import takagi.ru.monica.steam.library.family.SteamFamilyLibraryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.ownership.data.SteamStoreAppOwnershipService
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextGateway

class SteamStorePurchaseContextService(
    private val api: SteamApiClient = SteamApiClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamStorePurchaseContextGateway {
    private val familyService = SteamFamilyLibraryService(api)
    private val appOwnershipService = SteamStoreAppOwnershipService(api)
    private val familyCache = ConcurrentHashMap<String, CachedFamily>()

    override fun fetch(
        account: SteamAccount,
        appId: Int,
        language: String
    ): SteamStorePurchaseContext {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(appId > 0) { "positive app id required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw SteamStorePurchaseContextSessionException()
        val ownedAppIds = parseOwnedAppIds(
            api.callProtobuf(
                iface = "IPlayerService",
                method = "GetOwnedGames",
                request = SteamProtoWriter().apply {
                    writeUint64(1, account.steamId.toLong())
                    writeBool(2, false)
                    writeBool(3, true)
                    writeVarint(5, appId.toLong())
                    writeString(7, language)
                },
                accessToken = accessToken,
                useGet = true
            )
        )
        if (appId in ownedAppIds) {
            return context(
                account = account,
                appId = appId,
                ownership = SteamStoreOwnershipStatus.OWNED
            )
        }
        if (appOwnershipService.isOwned(appId, accessToken)) {
            return context(
                account = account,
                appId = appId,
                ownership = SteamStoreOwnershipStatus.OWNED
            )
        }

        val family = familyFor(account, language)
        val sharedGame = family.games.firstOrNull { it.appId == appId }
        return when {
            sharedGame != null -> context(
                account = account,
                appId = appId,
                ownership = SteamStoreOwnershipStatus.FAMILY_SHARED,
                familyGroupId = family.familyGroupId,
                ownerSteamIds = sharedGame.ownerSteamIds
            )
            family.failure != null -> context(
                account = account,
                appId = appId,
                ownership = SteamStoreOwnershipStatus.UNKNOWN,
                familyGroupId = family.familyGroupId,
                failure = family.failure.toPurchaseFailure()
            )
            else -> context(
                account = account,
                appId = appId,
                ownership = SteamStoreOwnershipStatus.NOT_OWNED,
                familyGroupId = family.familyGroupId
            )
        }
    }

    private fun familyFor(account: SteamAccount, language: String): SteamFamilyLibraryFetch {
        val key = "${account.steamId}|$language"
        val now = nowMillis()
        familyCache[key]?.takeIf { it.expiresAt > now }?.let { return it.value }
        return familyService.fetch(account, language).also { value ->
            if (value.failure == null) {
                familyCache[key] = CachedFamily(
                    value = value,
                    expiresAt = now + FAMILY_CACHE_TTL_MILLIS
                )
            }
        }
    }

    private fun context(
        account: SteamAccount,
        appId: Int,
        ownership: SteamStoreOwnershipStatus,
        familyGroupId: Long? = null,
        ownerSteamIds: List<String> = emptyList(),
        failure: SteamStorePurchaseContextFailure? = null
    ) = SteamStorePurchaseContext(
        accountSteamId = account.steamId,
        appId = appId,
        ownership = ownership,
        familyGroupId = familyGroupId,
        ownerSteamIds = ownerSteamIds,
        failure = failure,
        fetchedAt = nowMillis()
    )

    private data class CachedFamily(
        val value: SteamFamilyLibraryFetch,
        val expiresAt: Long
    )

    companion object {
        private const val FAMILY_CACHE_TTL_MILLIS = 5L * 60L * 1_000L

        internal fun parseOwnedAppIds(response: ByteArray): Set<Int> {
            val fields = SteamProtoReader(response).parseAll()
            require(fields.isNotEmpty()) { "Steam ownership response is empty" }
            val declaredCount = fields.firstOrNull { it.number == 1 && it.wireType == 0 }
                ?.asInt
            val appFields = fields.filter { it.number == 2 && it.bytes != null }
            require(declaredCount == null || declaredCount == appFields.size) {
                "Steam ownership response count mismatch"
            }
            return appFields.mapNotNullTo(linkedSetOf()) { field ->
                SteamProtoReader(field.bytes ?: return@mapNotNullTo null)
                    .parse()
                    .get(1)
                    ?.asLong
                    ?.toInt()
                    ?.takeIf { it > 0 }
            }
        }
    }
}

class SteamStorePurchaseContextSessionException(
    message: String = "Steam purchase context session required"
) : IllegalStateException(message)

private fun SteamLibraryFailureReason.toPurchaseFailure(): SteamStorePurchaseContextFailure =
    when (this) {
        SteamLibraryFailureReason.SESSION_REQUIRED ->
            SteamStorePurchaseContextFailure.SESSION_REQUIRED
        SteamLibraryFailureReason.RATE_LIMITED -> SteamStorePurchaseContextFailure.RATE_LIMITED
        SteamLibraryFailureReason.NETWORK -> SteamStorePurchaseContextFailure.NETWORK
        SteamLibraryFailureReason.PRIVATE_PROFILE,
        SteamLibraryFailureReason.INVALID_RESPONSE -> SteamStorePurchaseContextFailure.INVALID_RESPONSE
    }
