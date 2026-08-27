package takagi.ru.monica.steam.friends.chat.richmedia.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class SteamChatBeginUploadResponse(
    val cloudUrl: String,
    val requestHeaders: List<Pair<String, String>>,
    val ugcId: String,
    val timestamp: Long,
    val hmac: String
)

internal data class SteamChatCommitUploadResponse(
    val url: String
)

internal class SteamChatAttachmentUploadResponseParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun parseBegin(raw: String): SteamChatBeginUploadResponse {
        val payload = parseObject(raw, "begin Steam chat attachment")
        payload.requireSuccess("begin Steam chat attachment")
        val result = payload["result"] as? JsonObject
            ?: throw SteamChatUploadException("Steam did not issue an attachment upload URL")
        if (result["use_https"].truthy() != true) {
            throw SteamChatUploadException("Steam returned an insecure upload URL")
        }
        val host = result.string("url_host").trim()
        val path = result.string("url_path")
        val cloudUrl = "https://$host$path".toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host.isNotBlank() }
            ?.toString()
            ?: throw SteamChatUploadException("Steam returned an invalid upload URL")
        val requestHeaders = (result["request_headers"] as? JsonArray)
            ?.mapNotNull { value ->
                val header = value as? JsonObject ?: return@mapNotNull null
                val name = header.string("name").trim()
                val content = header.string("value")
                if (name.isBlank() || name.isBlockedUploadHeader()) null else name to content
            }
            ?: throw SteamChatUploadException("Steam returned incomplete upload headers")
        return SteamChatBeginUploadResponse(
            cloudUrl = cloudUrl,
            requestHeaders = requestHeaders,
            ugcId = result.string("ugcid"),
            timestamp = payload.long("timestamp"),
            hmac = payload.string("hmac")
        ).also { parsed ->
            if (parsed.ugcId.isBlank() || parsed.timestamp <= 0L || parsed.hmac.isBlank()) {
                throw SteamChatUploadException("Steam returned incomplete upload credentials")
            }
        }
    }

    fun parseFailure(raw: String): SteamChatUploadException? {
        if (raw.isBlank()) return null
        val payload = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return null
        val code = payload["success"].successCode() ?: return null
        if (code == 1) return null
        val detail = payload.string("message")
            .ifBlank { (payload["result"] as? JsonObject)?.string("message").orEmpty() }
        return SteamChatUploadException.steamRejected(code, detail)
    }

    fun parseCommit(raw: String): SteamChatCommitUploadResponse {
        val payload = parseObject(raw, "commit Steam chat attachment")
        payload.requireSuccess("commit Steam chat attachment")
        val result = payload["result"] as? JsonObject
            ?: throw SteamChatUploadException("Steam returned an incomplete attachment result")
        result.requireSuccess("commit Steam chat attachment")
        val details = result["details"] as? JsonObject
            ?: throw SteamChatUploadException("Steam returned no attachment details")
        val url = details.string("url").trim().toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host.isNotBlank() }
            ?.toString()
            ?: throw SteamChatUploadException("Steam returned an invalid attachment URL")
        return SteamChatCommitUploadResponse(url)
    }

    private fun parseObject(raw: String, operation: String): JsonObject {
        if (raw.isBlank()) throw SteamChatUploadException("Steam returned empty attachment data")
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            throw SteamChatUploadException("Steam returned invalid data while trying to $operation", it)
        }
    }

    private fun JsonObject.requireSuccess(operation: String) {
        val code = this["success"].successCode()
            ?: throw SteamChatUploadException("Steam omitted the result for $operation")
        if (code != 1) {
            val detail = string("message")
                .ifBlank { (this["result"] as? JsonObject)?.string("message").orEmpty() }
                .takeIf(String::isNotBlank)
            throw SteamChatUploadException.steamRejected(
                code = code,
                message = detail ?: "Steam rejected $operation (result $code)"
            )
        }
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonElement?.successCode(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        primitive.booleanOrNull?.let { return if (it) 1 else 0 }
        return when (primitive.contentOrNull?.lowercase()) {
            "true" -> 1
            "false" -> 0
            else -> null
        }
    }

    private fun JsonElement?.truthy(): Boolean? = when (successCode()) {
        1 -> true
        0 -> false
        else -> null
    }

    private fun String.isBlockedUploadHeader(): Boolean =
        equals("Host", true) ||
            equals("Content-Length", true)
}
