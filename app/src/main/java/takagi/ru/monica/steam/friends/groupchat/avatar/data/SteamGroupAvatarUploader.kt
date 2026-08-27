package takagi.ru.monica.steam.friends.groupchat.avatar.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.avatar.domain.SteamGroupAvatarUploadGateway
import takagi.ru.monica.steam.network.SteamHttpClientProvider

class SteamGroupAvatarUploader(
    context: Context,
    private val client: OkHttpClient = SteamHttpClientProvider.client
) : SteamGroupAvatarUploadGateway {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun upload(account: SteamAccount, rawUri: String): ByteArray {
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: error("Steam community session required for group avatar")
        val uri = Uri.parse(rawUri)
        val mimeType = resolver.getType(uri).orEmpty().takeIf { it.startsWith("image/") }
            ?: error("Please select an image")
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            stream.readLimited(MAX_AVATAR_BYTES + 1)
        } ?: error("Unable to read selected image")
        require(bytes.isNotEmpty()) { "Selected image is empty" }
        require(bytes.size <= MAX_AVATAR_BYTES) { "Steam group avatar must be 5 MB or smaller" }
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sessionid", sessionId)
            .addFormDataPart(
                "avatar",
                displayName(uri),
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://steamcommunity.com")
            .header("Referer", "https://steamcommunity.com/chat/")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "steamLoginSecure=$secure; sessionid=$sessionId")
            .post(body)
            .build()
        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Steam avatar upload failed (${response.code})")
            response.body?.string().orEmpty()
        }
        return parseSteamGroupAvatarSha(responseBody)
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "avatar.jpg" }
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index).orEmpty().ifBlank { name }
            }
        }
        return name
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        const val UPLOAD_URL = "https://steamcommunity.com/chat/avatarfileupload/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
    }
}

internal fun parseSteamGroupAvatarSha(responseBody: String): ByteArray {
    val sha = Json.parseToJsonElement(responseBody).jsonObject["sha"]
        ?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    require(sha.matches(Regex("[0-9a-fA-F]{40}"))) { "Steam returned an invalid avatar SHA" }
    return sha.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
