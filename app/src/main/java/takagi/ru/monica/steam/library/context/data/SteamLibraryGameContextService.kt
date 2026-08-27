package takagi.ru.monica.steam.library.context.data

import java.io.IOException
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcOwnership
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContextGateway
import takagi.ru.monica.steam.library.family.SteamFamilyLibraryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.ownership.data.SteamStoreAppOwnershipService

class SteamLibraryGameContextService(
    private val api: SteamApiClient = SteamApiClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamLibraryGameContextGateway {
    private val familyService = SteamFamilyLibraryService(api)
    private val appOwnershipService = SteamStoreAppOwnershipService(api)

    override fun fetch(
        account: SteamAccount,
        game: SteamGame,
        countryCode: String,
        language: String
    ): SteamLibraryResult<SteamLibraryGameContext> {
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        if (!account.hasRealSteamId || game.appId <= 0) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }

        val storeApp = fetchStoreAppMetadata(
            appId = game.appId,
            countryCode = countryCode,
            language = language
        )
        val dlcAppIds = storeApp.value?.dlcAppIds.orEmpty()
        val dlcMetadata = fetchDlcStoreMetadata(
            appIds = dlcAppIds,
            countryCode = countryCode,
            language = language,
            accessToken = accessToken
        )
        if (dlcMetadata.failure == SteamLibraryFailureReason.SESSION_REQUIRED) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }
        val dlcOwnership = fetchDlcOwnership(
            account = account,
            appIds = dlcAppIds,
            language = language,
            accessToken = accessToken
        )
        if (dlcOwnership.failure == SteamLibraryFailureReason.SESSION_REQUIRED) {
            return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
        }

        val supportsSteamCloud = storeApp.value?.supportsSteamCloud
            ?: game.supportsSteamCloud
        val cloud = when (supportsSteamCloud) {
            true -> fetchCloud(game.appId, accessToken).let { result ->
                if (result.failure == SteamLibraryFailureReason.SESSION_REQUIRED) {
                    return SteamLibraryResult.Failure(SteamLibraryFailureReason.SESSION_REQUIRED)
                }
                result.value ?: SteamLibraryCloudContext(
                    status = SteamLibraryCloudStatus.UNKNOWN,
                    failure = result.failure
                )
            }
            false -> SteamLibraryCloudContext(
                status = SteamLibraryCloudStatus.NOT_SUPPORTED
            )
            null -> SteamLibraryCloudContext(
                status = SteamLibraryCloudStatus.UNKNOWN,
                failure = storeApp.failure
            )
        }
        val dlc = dlcAppIds.map { appId ->
            val metadata = dlcMetadata.value?.get(appId)
            val ownership = dlcOwnership.value?.statuses?.get(appId)
                ?: SteamLibraryDlcOwnership.UNKNOWN
            SteamLibraryDlcContext(
                appId = appId,
                name = metadata?.name
                    ?.takeIf(String::isNotBlank)
                    ?: dlcOwnership.value?.sharedNames?.get(appId).orEmpty()
                    .ifBlank { "DLC #$appId" },
                headerImageUrl = metadata?.headerImageUrl.orEmpty(),
                ownership = ownership,
                ownerSteamIds = dlcOwnership.value?.ownerSteamIds?.get(appId).orEmpty()
            )
        }
        return SteamLibraryResult.Success(
            SteamLibraryGameContext(
                accountSteamId = account.steamId,
                appId = game.appId,
                ownership = game.ownership,
                ownerSteamIds = game.ownerSteamIds,
                supportsSteamCloud = supportsSteamCloud,
                cloud = cloud,
                dlc = dlc,
                dlcMetadataFailure = storeApp.failure ?: dlcMetadata.failure,
                dlcOwnershipFailure = dlcOwnership.failure,
                fetchedAt = nowMillis()
            )
        )
    }

    private fun fetchStoreAppMetadata(
        appId: Int,
        countryCode: String,
        language: String
    ): Partial<SteamLibraryStoreAppMetadata> = runCatching {
        SteamLibraryGameContextParser.parseStoreAppMetadata(
            appId = appId,
            payload = api.steamStoreGetJson(
                appId = appId,
                currency = countryCode.ifBlank { DEFAULT_COUNTRY_CODE }.uppercase(),
                language = language
            )
        )
    }.fold(
        onSuccess = { Partial(value = it) },
        onFailure = { Partial(failure = failureReason(it)) }
    )

    private fun fetchDlcStoreMetadata(
        appIds: List<Int>,
        countryCode: String,
        language: String,
        accessToken: String
    ): Partial<Map<Int, SteamLibraryDlcStoreMetadata>> {
        if (appIds.isEmpty()) return Partial(value = emptyMap())
        val items = linkedMapOf<Int, SteamLibraryDlcStoreMetadata>()
        var firstFailure: SteamLibraryFailureReason? = null
        for (batch in appIds.distinct().chunked(STORE_ITEM_BATCH_SIZE)) {
            if (firstFailure == SteamLibraryFailureReason.SESSION_REQUIRED) break
            runCatching {
                SteamLibraryGameContextParser.parseDlcStoreItems(
                    api.callProtobuf(
                        iface = "IStoreBrowseService",
                        method = "GetItems",
                        request = SteamProtoWriter().apply {
                            batch.forEach { appId ->
                                writeMessage(1, SteamProtoWriter().apply {
                                    writeVarint(1, appId.toLong())
                                })
                            }
                            writeMessage(2, SteamProtoWriter().apply {
                                writeString(1, language)
                                writeString(
                                    3,
                                    countryCode.ifBlank { DEFAULT_COUNTRY_CODE }.uppercase()
                                )
                            })
                            writeMessage(3, SteamProtoWriter().apply {
                                writeBool(1, true)
                                writeBool(10, true)
                            })
                        },
                        accessToken = accessToken,
                        useGet = true
                    )
                )
            }.onSuccess { parsed ->
                items += parsed
            }.onFailure { error ->
                if (firstFailure == null) firstFailure = failureReason(error)
            }
        }
        return Partial(value = items, failure = firstFailure)
    }

    private fun fetchDlcOwnership(
        account: SteamAccount,
        appIds: List<Int>,
        language: String,
        accessToken: String
    ): Partial<SteamLibraryDlcOwnershipSnapshot> {
        if (appIds.isEmpty()) {
            return Partial(value = SteamLibraryDlcOwnershipSnapshot())
        }
        val ownedIds = runCatching {
            SteamLibraryGameContextParser.parseOwnedDlcAppIds(
                api.callProtobuf(
                    iface = "IPlayerService",
                    method = "GetOwnedGames",
                    request = SteamProtoWriter().apply {
                        writeUint64(1, account.steamId.toLong())
                        writeBool(2, false)
                        writeBool(3, true)
                        writePackedVarints(5, appIds.distinct().map(Int::toLong))
                        writeString(7, language)
                    },
                    accessToken = accessToken,
                    useGet = true
                )
            )
        }.getOrElse { error ->
            return Partial(
                value = SteamLibraryDlcOwnershipSnapshot(
                    statuses = appIds.associateWith { SteamLibraryDlcOwnership.UNKNOWN }
                ),
                failure = failureReason(error)
            )
        }
        val interestOwnedIds = linkedSetOf<Int>()
        val interestFailures = linkedMapOf<Int, SteamLibraryFailureReason>()
        appIds.filterNot { it in ownedIds }.forEach { appId ->
            runCatching {
                appOwnershipService.isOwned(appId, accessToken)
            }.onSuccess { owned ->
                if (owned) interestOwnedIds += appId
            }.onFailure { error ->
                interestFailures[appId] = failureReason(error)
            }
        }
        val remainingIds = appIds.filterNot { it in ownedIds || it in interestOwnedIds }
        val family = if (remainingIds.isEmpty()) null else familyService.fetch(account, language)
        val sharedById = family?.games.orEmpty()
            .filter { it.appId in remainingIds }
            .associateBy(SteamGame::appId)
        val statuses = appIds.associateWith { appId ->
            when {
                appId in ownedIds -> SteamLibraryDlcOwnership.OWNED
                appId in interestOwnedIds -> SteamLibraryDlcOwnership.OWNED
                appId in sharedById -> SteamLibraryDlcOwnership.FAMILY_SHARED
                appId in interestFailures -> SteamLibraryDlcOwnership.UNKNOWN
                family?.failure != null -> SteamLibraryDlcOwnership.UNKNOWN
                else -> SteamLibraryDlcOwnership.NOT_OWNED
            }
        }
        return Partial(
            value = SteamLibraryDlcOwnershipSnapshot(
                statuses = statuses,
                ownerSteamIds = sharedById.mapValues { it.value.ownerSteamIds },
                sharedNames = sharedById.mapValues { it.value.name }
            ),
            failure = interestFailures.values.firstOrNull() ?: family?.failure
        )
    }

    private fun fetchCloud(
        appId: Int,
        accessToken: String
    ): Partial<SteamLibraryCloudContext> = runCatching {
        SteamLibraryGameContextParser.parseCloud(
            api.callProtobuf(
                iface = "ICloudService",
                method = "GetAppFileChangelist",
                request = SteamProtoWriter().apply {
                    writeVarint(1, appId.toLong())
                    writeUint64(2, 0L)
                },
                accessToken = accessToken,
                useGet = true
            )
        )
    }.fold(
        onSuccess = { Partial(value = it) },
        onFailure = { Partial(failure = failureReason(it)) }
    )

    private fun failureReason(error: Throwable): SteamLibraryFailureReason = when (error) {
        is SteamLibraryContextException -> error.reason
        is SteamApiException -> when {
            error.eResult == 5 || error.eResult == 15 ||
                error.eResult == 401 || error.eResult == 403 ||
                error.httpStatusCode == 401 || error.httpStatusCode == 403 ->
                SteamLibraryFailureReason.SESSION_REQUIRED
            error.eResult == 429 || error.httpStatusCode == 429 ->
                SteamLibraryFailureReason.RATE_LIMITED
            else -> SteamLibraryFailureReason.NETWORK
        }
        is IOException -> SteamLibraryFailureReason.NETWORK
        is IllegalArgumentException,
        is IllegalStateException,
        is IndexOutOfBoundsException -> SteamLibraryFailureReason.INVALID_RESPONSE
        else -> SteamLibraryFailureReason.NETWORK
    }

    private data class Partial<T>(
        val value: T? = null,
        val failure: SteamLibraryFailureReason? = null
    )

    private data class SteamLibraryDlcOwnershipSnapshot(
        val statuses: Map<Int, SteamLibraryDlcOwnership> = emptyMap(),
        val ownerSteamIds: Map<Int, List<String>> = emptyMap(),
        val sharedNames: Map<Int, String> = emptyMap()
    )

    private companion object {
        const val STORE_ITEM_BATCH_SIZE = 40
        const val DEFAULT_COUNTRY_CODE = "CN"
    }
}
