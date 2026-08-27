package takagi.ru.monica.steam.ownership.data

import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

internal object SteamStoreAppOwnershipParser {
    fun parse(response: ByteArray): Boolean =
        SteamProtoReader(response).parseAll().any { field ->
            field.number == 1 && field.wireType == 0 && field.asInt == 1
        }
}

internal class SteamStoreAppOwnershipService(
    private val api: SteamApiClient
) {
    fun isOwned(appId: Int, accessToken: String): Boolean =
        SteamStoreAppOwnershipParser.parse(
            api.callProtobuf(
                iface = "IStoreService",
                method = "GetUserGameInterestState",
                request = SteamProtoWriter().apply {
                    writeVarint(1, appId.toLong())
                },
                accessToken = accessToken,
                useGet = true
            )
        )
}
