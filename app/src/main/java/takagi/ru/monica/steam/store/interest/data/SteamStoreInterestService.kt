package takagi.ru.monica.steam.store.interest.data

import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.data.SteamStoreIgnoreSessionException
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.effectiveSteamStoreAccessToken
import takagi.ru.monica.steam.store.data.encodeSteamCookieValue
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestAccount

internal class SteamStoreInterestService(
    private val client: OkHttpClient,
    private val api: SteamApiClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sessionIdFactory: () -> String = ::newSteamStoreSessionId
) : SteamStoreInterestRemoteDataSource {
    private val ignoredAppsByAccount = ConcurrentHashMap<Long, IgnoredAppsCacheEntry>()

    @Synchronized
    fun ignoredAppIds(
        steamId: String?,
        steamLoginSecure: String?,
        accessToken: String?,
        countryCode: String,
        forceRefresh: Boolean = false
    ): Set<Int> {
        val accountId = steamDynamicStoreAccountId(steamId) ?: return emptySet()
        val cached = ignoredAppsByAccount[accountId]
        if (!forceRefresh && cached != null && nowMillis() - cached.fetchedAt < CACHE_TTL_MILLIS) {
            return cached.appIds
        }
        val loginSecure = effectiveSteamStoreLoginSecure(
            steamId = steamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken
        ) ?: throw SteamStoreIgnoreSessionException()
        return try {
            client.newCall(
                buildSteamIgnoredAppsRequest(
                    steamId = requireNotNull(steamId),
                    steamLoginSecure = loginSecure,
                    countryCode = countryCode
                )
            ).execute().use { response ->
                if (response.code in 300..399 || response.code == 401 || response.code == 403) {
                    throw SteamStoreIgnoreSessionException()
                }
                if (!response.isSuccessful) {
                    throw IOException("Steam ignored games request failed: ${response.code}")
                }
                val body = response.body?.string()?.takeIf(String::isNotBlank)
                    ?: throw IOException("Steam ignored games response is empty")
                parseSteamIgnoredAppIds(body).also { appIds ->
                    ignoredAppsByAccount[accountId] = IgnoredAppsCacheEntry(
                        appIds = appIds,
                        fetchedAt = nowMillis()
                    )
                }
            }
        } catch (error: Throwable) {
            cached?.appIds ?: throw error
        }
    }

    override fun ignoredAppIds(
        account: SteamStoreInterestAccount,
        forceRefresh: Boolean
    ): Set<Int> = ignoredAppIds(
        steamId = account.steamId,
        steamLoginSecure = account.steamLoginSecure,
        accessToken = account.accessToken,
        countryCode = account.countryCode,
        forceRefresh = forceRefresh
    )

    fun isIgnored(
        appId: Int,
        steamId: String?,
        steamLoginSecure: String?,
        accessToken: String?
    ): Boolean {
        val accountId = steamDynamicStoreAccountId(steamId)
        if (appId <= 0 || accountId == null) return false
        val token = effectiveSteamStoreAccessToken(accessToken, steamLoginSecure)
            ?: throw SteamStoreIgnoreSessionException()
        return try {
            parseSteamGameInterestState(
                api.callProtobuf(
                    iface = "IStoreService",
                    method = "GetUserGameInterestState",
                    request = buildSteamGameInterestStateRequest(appId),
                    accessToken = token,
                    useGet = false
                )
            )
        } catch (error: Throwable) {
            if (error is SteamApiException && error.eResult in setOf(5, 15, 401, 403)) {
                throw SteamStoreIgnoreSessionException()
            }
            ignoredAppsByAccount[accountId]?.appIds?.contains(appId) ?: throw error
        }
    }

    override fun isIgnored(
        appId: Int,
        account: SteamStoreInterestAccount
    ): Boolean = isIgnored(
        appId = appId,
        steamId = account.steamId,
        steamLoginSecure = account.steamLoginSecure,
        accessToken = account.accessToken
    )

    @Synchronized
    fun setIgnored(
        appId: Int,
        ignored: Boolean,
        steamId: String?,
        steamLoginSecure: String?,
        accessToken: String?
    ) {
        require(appId > 0) { "invalid Steam app id" }
        val accountId = steamDynamicStoreAccountId(steamId)
            ?: throw SteamStoreIgnoreSessionException()
        val loginSecure = effectiveSteamStoreLoginSecure(
            steamId = steamId,
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken
        ) ?: throw SteamStoreIgnoreSessionException()
        client.newCall(
            buildSteamIgnoreMutationRequest(
                appId = appId,
                ignored = ignored,
                steamLoginSecure = loginSecure,
                sessionId = sessionIdFactory()
            )
        ).execute().use { response ->
            if (response.code in 300..399 || response.code == 401 || response.code == 403) {
                throw SteamStoreIgnoreSessionException()
            }
            if (!response.isSuccessful) {
                throw IOException("Steam ignore request failed: ${response.code}")
            }
        }
        val current = ignoredAppsByAccount[accountId]?.appIds.orEmpty()
        ignoredAppsByAccount[accountId] = IgnoredAppsCacheEntry(
            appIds = if (ignored) current + appId else current - appId,
            fetchedAt = nowMillis()
        )
    }

    override fun setIgnored(
        appId: Int,
        ignored: Boolean,
        account: SteamStoreInterestAccount
    ) = setIgnored(
        appId = appId,
        ignored = ignored,
        steamId = account.steamId,
        steamLoginSecure = account.steamLoginSecure,
        accessToken = account.accessToken
    )

    private data class IgnoredAppsCacheEntry(
        val appIds: Set<Int>,
        val fetchedAt: Long
    )

    private companion object {
        const val CACHE_TTL_MILLIS = 5L * 60L * 1_000L
    }
}

internal fun buildSteamIgnoredAppsRequest(
    steamId: String,
    steamLoginSecure: String,
    countryCode: String
): Request {
    val accountId = steamDynamicStoreAccountId(steamId)
        ?: throw IllegalArgumentException("invalid Steam ID")
    return buildSteamStoreRequest(
        path = "/dynamicstore/userdata/",
        query = mapOf("id" to accountId.toString()),
        steamLoginSecure = steamLoginSecure,
        countryCode = countryCode.trim().uppercase()
    )
}

internal fun parseSteamIgnoredAppIds(body: String): Set<Int> {
    val ignored = Json.parseToJsonElement(body).jsonObject["rgIgnoredApps"]
        ?: throw IllegalArgumentException("Steam dynamic store response has no ignored-app state")
    return when (ignored) {
        is JsonObject -> ignored.keys.asSequence()
        is JsonArray -> ignored.asSequence().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }
        else -> emptySequence()
    }.mapNotNull(String::toIntOrNull)
        .filter { it > 0 }
        .toCollection(linkedSetOf())
}

internal fun buildSteamGameInterestStateRequest(appId: Int): SteamProtoWriter =
    SteamProtoWriter().apply { writeVarint(1, appId.coerceAtLeast(0).toLong()) }

internal fun parseSteamGameInterestState(response: ByteArray): Boolean =
    SteamProtoReader(response).parse()[3]?.asBool == true

internal fun buildSteamIgnoreMutationRequest(
    appId: Int,
    ignored: Boolean,
    steamLoginSecure: String,
    sessionId: String
): Request {
    val body = FormBody.Builder()
        .add("sessionid", sessionId)
        .add("appid", appId.toString())
        .apply {
            if (ignored) add("ignore_reason", "0") else add("remove", "1")
        }
        .build()
    return Request.Builder()
        .url("https://store.steampowered.com/recommended/ignorerecommendation/")
        .header("User-Agent", "Etoile/1.0")
        .header("Accept", "application/json, text/plain, */*")
        .header("Referer", "https://store.steampowered.com/app/$appId/")
        .header(
            "Cookie",
            "sessionid=${encodeSteamCookieValue(sessionId)}; " +
                "steamLoginSecure=${encodeSteamCookieValue(steamLoginSecure)}"
        )
        .post(body)
        .build()
}

internal fun steamDynamicStoreAccountId(steamId: String?): Long? {
    val value = steamId?.trim()?.toULongOrNull() ?: return null
    if (value < STEAM_ID64_ACCOUNT_BASE) return null
    return (value - STEAM_ID64_ACCOUNT_BASE)
        .takeIf { it <= UInt.MAX_VALUE.toULong() }
        ?.toLong()
}

internal fun effectiveSteamStoreLoginSecure(
    steamId: String?,
    steamLoginSecure: String?,
    accessToken: String?
): String? = steamId?.trim()?.takeIf(String::isNotBlank)?.let { id ->
    accessToken?.trim()?.takeIf(String::isNotBlank)?.let { token -> "$id||$token" }
} ?: steamLoginSecure?.trim()?.takeIf(String::isNotBlank)

private fun newSteamStoreSessionId(): String = ByteArray(12)
    .also(SecureRandom()::nextBytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private val STEAM_ID64_ACCOUNT_BASE = 76_561_197_960_265_728uL
