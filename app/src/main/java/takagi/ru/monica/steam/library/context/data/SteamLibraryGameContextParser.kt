package takagi.ru.monica.steam.library.context.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.network.SteamProtoReader

internal data class SteamLibraryStoreAppMetadata(
    val dlcAppIds: List<Int>,
    val supportsSteamCloud: Boolean?
)

internal data class SteamLibraryDlcStoreMetadata(
    val appId: Int,
    val name: String,
    val headerImageUrl: String
)

internal object SteamLibraryGameContextParser {
    fun parseStoreAppMetadata(
        appId: Int,
        payload: JsonObject
    ): SteamLibraryStoreAppMetadata {
        val wrapper = payload[appId.toString()] as? JsonObject
            ?: throw SteamLibraryContextException(SteamLibraryFailureReason.INVALID_RESPONSE)
        if ((wrapper["success"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw SteamLibraryContextException(SteamLibraryFailureReason.INVALID_RESPONSE)
        }
        val data = wrapper["data"] as? JsonObject
            ?: throw SteamLibraryContextException(SteamLibraryFailureReason.INVALID_RESPONSE)
        val dlcAppIds = (data["dlc"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { id -> id > 0 } }
            .distinct()
        val categories = data["categories"] as? JsonArray
        val supportsSteamCloud = categories?.any { raw ->
            val category = raw as? JsonObject ?: return@any false
            (category["id"] as? JsonPrimitive)?.intOrNull == STEAM_CLOUD_CATEGORY_ID
        }
        return SteamLibraryStoreAppMetadata(
            dlcAppIds = dlcAppIds,
            supportsSteamCloud = supportsSteamCloud
        )
    }

    fun parseDlcStoreItems(response: ByteArray): Map<Int, SteamLibraryDlcStoreMetadata> {
        return SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { itemField ->
                val item = runCatching {
                    SteamProtoReader(itemField.bytes ?: return@mapNotNull null).parse()
                }.getOrNull() ?: return@mapNotNull null
                val appId = item[9]?.asLong?.toInt()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val assets = item[30]?.bytes?.let { SteamProtoReader(it).parse() }
                val assetFormat = assets?.get(1)?.asString.orEmpty()
                val assetFilename = sequenceOf(4, 2, 3)
                    .mapNotNull { field ->
                        assets?.get(field)?.asString?.takeIf(String::isNotBlank)
                    }
                    .firstOrNull()
                appId to SteamLibraryDlcStoreMetadata(
                    appId = appId,
                    name = item[6]?.asString.orEmpty(),
                    headerImageUrl = buildStoreAssetUrl(assetFormat, assetFilename)
                )
            }
            .toMap()
    }

    fun parseOwnedDlcAppIds(response: ByteArray): Set<Int> {
        val fields = SteamProtoReader(response).parseAll()
        val declaredCount = fields.firstOrNull { it.number == 1 && it.wireType == 0 }
            ?.asInt
        val gameFields = fields.filter { it.number == 2 && it.bytes != null }
        if (declaredCount != null && declaredCount != gameFields.size) {
            throw SteamLibraryContextException(SteamLibraryFailureReason.INVALID_RESPONSE)
        }
        return gameFields.mapNotNullTo(linkedSetOf()) { field ->
            SteamProtoReader(field.bytes ?: return@mapNotNullTo null)
                .parse()
                .get(1)
                ?.asLong
                ?.toInt()
                ?.takeIf { it > 0 }
        }
    }

    fun parseCloud(response: ByteArray): SteamLibraryCloudContext {
        val fields = SteamProtoReader(response).parseAll()
        val persistedFiles = fields.asSequence()
            .filter { it.number == 2 && it.bytes != null }
            .mapNotNull { field ->
                runCatching {
                    SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
                }.getOrNull()
            }
            .filter { file -> (file[5]?.asInt ?: PERSISTED_STATE) == PERSISTED_STATE }
            .toList()
        val totalBytes = persistedFiles.sumOf { file ->
            file[4]?.asLong?.coerceAtLeast(0L) ?: 0L
        }
        val latestTimestamp = persistedFiles.maxOfOrNull { file ->
            file[3]?.asLong?.coerceAtLeast(0L) ?: 0L
        }?.takeIf { it > 0L }
        val machineCount = fields.asSequence()
            .filter { it.number == 5 }
            .map { it.asString }
            .filter(String::isNotBlank)
            .distinct()
            .count()
        return SteamLibraryCloudContext(
            status = if (persistedFiles.isEmpty()) {
                SteamLibraryCloudStatus.EMPTY
            } else {
                SteamLibraryCloudStatus.AVAILABLE
            },
            fileCount = persistedFiles.size,
            totalBytes = totalBytes,
            lastUpdatedAtSeconds = latestTimestamp,
            currentChangeNumber = fields.firstOrNull { it.number == 1 }
                ?.asLong
                ?.takeIf { it > 0L },
            appBuildIdHighWaterMark = fields.firstOrNull { it.number == 6 }
                ?.asLong
                ?.takeIf { it > 0L },
            machineCount = machineCount
        )
    }

    private fun buildStoreAssetUrl(format: String, filename: String?): String {
        if (format.isBlank() || filename.isNullOrBlank()) return ""
        val resolved = format.replace("\${FILENAME}", filename)
        return if (resolved.startsWith("https://")) {
            resolved
        } else {
            STORE_ASSET_BASE + resolved.trimStart('/')
        }
    }

    private const val STEAM_CLOUD_CATEGORY_ID = 23
    private const val PERSISTED_STATE = 0
    private const val STORE_ASSET_BASE =
        "https://shared.akamai.steamstatic.com/store_item_assets/"
}

internal class SteamLibraryContextException(
    val reason: SteamLibraryFailureReason
) : IllegalStateException(reason.name)
