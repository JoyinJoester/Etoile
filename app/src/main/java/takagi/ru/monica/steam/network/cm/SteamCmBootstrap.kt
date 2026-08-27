package takagi.ru.monica.steam.network.cm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException

internal data class SteamCmBootstrapData(
    val steamId: Long,
    val webLogonToken: String,
    val endpoints: List<String>
)

internal fun interface SteamCmBootstrapLoader {
    fun load(account: SteamAccount): SteamCmBootstrapData
}

internal class SteamCmBootstrap(
    private val api: SteamApiClient = SteamApiClient()
) : SteamCmBootstrapLoader {
    override fun load(account: SteamAccount): SteamCmBootstrapData {
        require(account.hasRealSteamId) { "Real Steam ID required for Steam chat" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required for Steam chat")
        val steamId = account.steamId.toLong()
        val webSession = api.communityGetJson(
            path = CLIENT_JS_TOKEN_PATH,
            query = emptyMap(),
            cookies = mapOf(
                "steamLoginSecure" to "${account.steamId}||$accessToken"
            ),
            referer = "https://steamcommunity.com/chat/"
        )
        val loggedIn = webSession["logged_in"]?.jsonPrimitive?.booleanOrNull == true
        val responseSteamId = webSession.string("steamid")
        val webLogonToken = webSession.string("token")
        if (!loggedIn || responseSteamId != account.steamId || webLogonToken.isBlank()) {
            throw SteamApiException(
                message = "Steam chat web session expired",
                httpStatusCode = 401
            )
        }

        val cmPayload = api.steamApiGetJson(
            path = CM_LIST_PATH,
            query = mapOf(
                "cellid" to "0",
                "cmtype" to "websockets",
                "origin" to "https://steamcommunity.com"
            )
        )
        val endpoints = cmPayload["response"]
            ?.jsonObject
            ?.get("serverlist")
            ?.jsonArray
            .orEmpty()
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("type") == "websockets" }
            .filter { it.string("realm") == "steamglobal" }
            .mapNotNull { server ->
                server.string("endpoint")
                    .takeIf(::isAllowedEndpoint)
                    ?.let { endpoint -> endpoint to server.weightedLoad() }
            }
            .sortedBy(Pair<String, Double>::second)
            .map(Pair<String, Double>::first)
            .distinct()
            .toList()
        if (endpoints.isEmpty()) {
            throw SteamApiException("Steam CM server list is empty")
        }
        return SteamCmBootstrapData(
            steamId = steamId,
            webLogonToken = webLogonToken,
            endpoints = endpoints
        )
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.weightedLoad(): Double =
        this["wtd_load"]?.jsonPrimitive?.doubleOrNull ?: Double.MAX_VALUE

    private fun isAllowedEndpoint(endpoint: String): Boolean {
        val url = "https://$endpoint/".toHttpUrlOrNull() ?: return false
        if (url.port != 443) return false
        val host = url.host.lowercase()
        return host.endsWith(".steamserver.net") || host.endsWith(".steampowered.com")
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private companion object {
        const val CLIENT_JS_TOKEN_PATH = "/chat/clientjstoken"
        const val CM_LIST_PATH = "/ISteamDirectory/GetCMListForConnect/v1/"
    }
}
