package takagi.ru.monica.steam.library.context.domain

import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult

@Serializable
enum class SteamLibraryDlcOwnership {
    OWNED,
    FAMILY_SHARED,
    NOT_OWNED,
    UNKNOWN
}

@Serializable
data class SteamLibraryDlcContext(
    val appId: Int,
    val name: String = "",
    val headerImageUrl: String = "",
    val ownership: SteamLibraryDlcOwnership = SteamLibraryDlcOwnership.UNKNOWN,
    val ownerSteamIds: List<String> = emptyList()
)

@Serializable
enum class SteamLibraryCloudStatus {
    UNKNOWN,
    NOT_SUPPORTED,
    EMPTY,
    AVAILABLE
}

@Serializable
data class SteamLibraryCloudContext(
    val status: SteamLibraryCloudStatus = SteamLibraryCloudStatus.UNKNOWN,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val lastUpdatedAtSeconds: Long? = null,
    val currentChangeNumber: Long? = null,
    val appBuildIdHighWaterMark: Long? = null,
    val machineCount: Int = 0,
    val failure: SteamLibraryFailureReason? = null
)

@Serializable
data class SteamLibraryGameContext(
    val accountSteamId: String,
    val appId: Int,
    val ownership: SteamGameOwnership,
    val ownerSteamIds: List<String> = emptyList(),
    val supportsSteamCloud: Boolean? = null,
    val cloud: SteamLibraryCloudContext = SteamLibraryCloudContext(),
    val dlc: List<SteamLibraryDlcContext> = emptyList(),
    val dlcMetadataFailure: SteamLibraryFailureReason? = null,
    val dlcOwnershipFailure: SteamLibraryFailureReason? = null,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val ownedDlcCount: Int get() = dlc.count {
        it.ownership == SteamLibraryDlcOwnership.OWNED
    }
    val familySharedDlcCount: Int get() = dlc.count {
        it.ownership == SteamLibraryDlcOwnership.FAMILY_SHARED
    }
}

fun interface SteamLibraryGameContextGateway {
    fun fetch(
        account: SteamAccount,
        game: SteamGame,
        countryCode: String,
        language: String
    ): SteamLibraryResult<SteamLibraryGameContext>
}

internal data class SteamLibraryGameContextMerge(
    val context: SteamLibraryGameContext,
    val usedCache: Boolean
)

internal fun mergeSteamLibraryGameContext(
    fresh: SteamLibraryGameContext,
    cached: SteamLibraryGameContext?
): SteamLibraryGameContextMerge {
    if (cached == null ||
        cached.accountSteamId != fresh.accountSteamId ||
        cached.appId != fresh.appId
    ) {
        return SteamLibraryGameContextMerge(fresh, usedCache = false)
    }

    var usedCache = false
    val cachedDlc = cached.dlc.associateBy(SteamLibraryDlcContext::appId)
    val dlcToMerge = if (
        fresh.dlcMetadataFailure != null && fresh.dlc.isEmpty() && cached.dlc.isNotEmpty()
    ) {
        usedCache = true
        cached.dlc
    } else {
        fresh.dlc
    }
    val mergedDlc = dlcToMerge.map { item ->
        val previous = cachedDlc[item.appId]
        var merged = item
        if (fresh.dlcMetadataFailure != null && previous != null) {
            val cachedNameIsUseful = previous.name.isNotBlank() &&
                !previous.name.startsWith("DLC #")
            val freshNameNeedsFallback = item.name.isBlank() || item.name.startsWith("DLC #")
            if ((freshNameNeedsFallback && cachedNameIsUseful) ||
                (item.headerImageUrl.isBlank() && previous.headerImageUrl.isNotBlank())
            ) {
                usedCache = true
                merged = merged.copy(
                    name = if (freshNameNeedsFallback && cachedNameIsUseful) {
                        previous.name
                    } else {
                        merged.name
                    },
                    headerImageUrl = merged.headerImageUrl.ifBlank { previous.headerImageUrl }
                )
            }
        }
        if (fresh.dlcOwnershipFailure != null &&
            merged.ownership == SteamLibraryDlcOwnership.UNKNOWN &&
            previous != null
        ) {
            usedCache = true
            merged = merged.copy(
                ownership = previous.ownership,
                ownerSteamIds = previous.ownerSteamIds
            )
        }
        merged
    }

    val mergedCloud = if (
        fresh.cloud.failure != null &&
        cached.cloud.status != SteamLibraryCloudStatus.UNKNOWN
    ) {
        usedCache = true
        cached.cloud.copy(failure = fresh.cloud.failure)
    } else {
        fresh.cloud
    }
    val supportsSteamCloud = fresh.supportsSteamCloud ?: cached.supportsSteamCloud.also {
        if (it != null) usedCache = true
    }

    return SteamLibraryGameContextMerge(
        context = fresh.copy(
            supportsSteamCloud = supportsSteamCloud,
            cloud = mergedCloud,
            dlc = mergedDlc
        ),
        usedCache = usedCache
    )
}

internal fun steamLibraryGameContextIsCacheable(
    context: SteamLibraryGameContext
): Boolean = context.dlcMetadataFailure == null &&
    context.dlcOwnershipFailure == null &&
    context.cloud.failure == null
