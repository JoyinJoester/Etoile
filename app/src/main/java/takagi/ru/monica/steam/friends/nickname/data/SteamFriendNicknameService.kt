package takagi.ru.monica.steam.friends.nickname.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.nickname.domain.SteamFriendNicknameGateway
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamFriendNicknameService(
    private val cm: SteamCmGateway = SteamCmClient(),
    private val api: SteamApiClient = SteamApiClient()
) : SteamFriendNicknameGateway {
    override fun fetch(account: SteamAccount): Map<String, String> {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val response = runCatching {
            api.callProtobuf(
                iface = PLAYER_SERVICE_INTERFACE,
                method = GET_NICKNAME_LIST_WEB_METHOD,
                request = SteamProtoWriter(),
                accessToken = accessToken,
                useGet = true
            )
        }.getOrElse {
            cm.callService(
                account = account,
                method = GET_NICKNAME_LIST_CM_METHOD,
                request = ByteArray(0)
            )
        }
        return SteamFriendNicknameParser.parse(
            response
        )
    }

    private companion object {
        const val PLAYER_SERVICE_INTERFACE = "IPlayerService"
        const val GET_NICKNAME_LIST_WEB_METHOD = "GetNicknameList"
        const val GET_NICKNAME_LIST_CM_METHOD = "Player.GetNicknameList#1"
    }
}
