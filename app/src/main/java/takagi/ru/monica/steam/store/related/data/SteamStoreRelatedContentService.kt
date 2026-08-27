package takagi.ru.monica.steam.store.related.data

import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.related.domain.SteamStoreRelatedApp

internal object SteamStoreRelatedContentParser {
    fun parse(response: ByteArray): List<SteamStoreRelatedApp> =
        SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val item = runCatching {
                    SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
                }.getOrNull() ?: return@mapNotNull null
                val appId = item[9]?.asLong?.toInt()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val name = item[6]?.asString.orEmpty().trim()
                if (name.isBlank()) return@mapNotNull null
                val assets = item[30]?.bytes?.let { SteamProtoReader(it).parse() }
                val format = assets?.get(1)?.asString.orEmpty()
                val filename = sequenceOf(4, 2, 3)
                    .mapNotNull { number ->
                        assets?.get(number)?.asString?.takeIf(String::isNotBlank)
                    }
                    .firstOrNull()
                SteamStoreRelatedApp(
                    appId = appId,
                    name = name,
                    headerImageUrl = storeAssetUrl(format, filename)
                )
            }
            .distinctBy(SteamStoreRelatedApp::appId)
            .toList()

    private fun storeAssetUrl(format: String, filename: String?): String {
        if (format.isBlank() || filename.isNullOrBlank()) return ""
        val path = format.replace("\${FILENAME}", filename)
        return if (path.startsWith("https://")) {
            path
        } else {
            STORE_ASSET_BASE + path.trimStart('/')
        }
    }

    private const val STORE_ASSET_BASE =
        "https://shared.akamai.steamstatic.com/store_item_assets/"
}

internal class SteamStoreRelatedContentService(
    private val api: SteamApiClient
) {
    fun fetch(
        appIds: List<Int>,
        countryCode: String,
        language: String,
        accessToken: String?
    ): List<SteamStoreRelatedApp> {
        if (appIds.isEmpty()) return emptyList()
        val byId = linkedMapOf<Int, SteamStoreRelatedApp>()
        appIds.distinct().chunked(MAX_BATCH_SIZE).forEach { batch ->
            runCatching {
                SteamStoreRelatedContentParser.parse(
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
                                writeString(3, countryCode.ifBlank { "CN" }.uppercase())
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
            }.onSuccess { items ->
                items.forEach { byId[it.appId] = it }
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "store_related metadata_failed app_ids=${batch.joinToString()} " +
                        "type=${error.javaClass.simpleName}"
                )
            }
        }
        return appIds.mapNotNull(byId::get)
    }

    private companion object {
        const val MAX_BATCH_SIZE = 40
    }
}
