package takagi.ru.monica.steam.token.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.token.loginsecurity.data.SteamMobileAuthRequestProfile

internal data class SteamLoginRsaKey(
    val modulusHex: String,
    val exponentHex: String,
    val timestamp: String
)

internal enum class SteamLoginRsaSource {
    AUTH_API,
    COMMUNITY_FALLBACK
}

internal sealed interface SteamLoginRsaResult {
    data class Success(
        val key: SteamLoginRsaKey,
        val source: SteamLoginRsaSource
    ) : SteamLoginRsaResult

    data class Failure(val reason: String) : SteamLoginRsaResult
}

internal class SteamLoginRsaKeyProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val log: (String) -> Unit = SteamDiagLogger::append
) {
    fun load(accountName: String): SteamLoginRsaResult {
        val normalizedName = accountName.trim()
        requestAuthApi(normalizedName)?.let { key ->
            log("login_rsa source=auth_api result=success")
            return SteamLoginRsaResult.Success(key, SteamLoginRsaSource.AUTH_API)
        }
        log("login_rsa source=auth_api result=fallback")

        requestCommunityFallback(normalizedName)?.let { key ->
            log("login_rsa source=community result=success")
            return SteamLoginRsaResult.Success(key, SteamLoginRsaSource.COMMUNITY_FALLBACK)
        }
        log("login_rsa source=community result=failed")
        return SteamLoginRsaResult.Failure(
            "无法连接 Steam 密钥服务，已尝试新版与兼容接口，请切换网络节点后重试"
        )
    }

    private fun requestAuthApi(accountName: String): SteamLoginRsaKey? {
        val request = SteamMobileAuthRequestProfile.applyTo(
            Request.Builder()
                .url(
                    AUTH_API_URL.toHttpUrl().newBuilder()
                        .addQueryParameter("account_name", accountName)
                        .build()
                )
                .get()
        ).build()
        return execute(request, SteamLoginRsaSource.AUTH_API)
    }

    private fun requestCommunityFallback(accountName: String): SteamLoginRsaKey? {
        val body = FormBody.Builder()
            .add("username", accountName)
            .add("donotcache", System.currentTimeMillis().toString())
            .build()
        val request = Request.Builder()
            .url(COMMUNITY_RSA_URL)
            .post(body)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", "https://steamcommunity.com")
            .header("Referer", "https://steamcommunity.com/login/home/")
            .build()
        return execute(request, SteamLoginRsaSource.COMMUNITY_FALLBACK)
    }

    private fun execute(request: Request, source: SteamLoginRsaSource): SteamLoginRsaKey? =
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log(
                        "login_rsa source=${source.logName} result=http_${response.code}"
                    )
                    null
                } else {
                    val body = response.body?.string()?.takeIf(String::isNotBlank)
                    val root = body?.let { json.parseToJsonElement(it).jsonObject }
                    root?.let(::parseSteamLoginRsaKey)?.also {
                        log("login_rsa source=${source.logName} result=valid")
                    } ?: run {
                        log(
                            "login_rsa source=${source.logName} result=incomplete " +
                                "keys=${root?.keys?.sorted()?.joinToString(",").orEmpty()}"
                        )
                        null
                    }
                }
            }
        }.onFailure { error ->
            log(
                "login_rsa source=${source.logName} result=exception " +
                    "type=${error.javaClass.simpleName}"
            )
        }.getOrNull()

    private val SteamLoginRsaSource.logName: String
        get() = when (this) {
            SteamLoginRsaSource.AUTH_API -> "auth_api"
            SteamLoginRsaSource.COMMUNITY_FALLBACK -> "community"
        }

    private companion object {
        const val AUTH_API_URL =
            "https://api.steampowered.com/IAuthenticationService/GetPasswordRSAPublicKey/v1"
        const val COMMUNITY_RSA_URL = "https://steamcommunity.com/login/getrsakey/"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}

internal fun parseSteamLoginRsaKey(root: JsonObject): SteamLoginRsaKey? {
    val payload = (root["response"] as? JsonObject) ?: root
    val modulus = payload.stringValue("publickey_mod", "publickey_modulus")
    val exponent = payload.stringValue("publickey_exp", "publickey_exponent")
    val timestamp = payload.stringValue("timestamp", "rsatimestamp")
    if (
        modulus.isNullOrBlank() || exponent.isNullOrBlank() || timestamp.isNullOrBlank() ||
        !modulus.matches(HEX_VALUE) || !exponent.matches(HEX_VALUE) ||
        !timestamp.all(Char::isDigit)
    ) return null
    return SteamLoginRsaKey(modulus, exponent, timestamp)
}

private fun JsonObject.stringValue(vararg keys: String): String? {
    keys.forEach { key ->
        val value = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private val HEX_VALUE = Regex("[0-9a-fA-F]+")
