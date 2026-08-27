package takagi.ru.monica.steam.store.purchase.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption

internal data class SteamStorePackageMetadata(
    val name: String,
    val imageUrl: String
)

internal object SteamStorePackageMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(packageId: Int, payload: String): SteamStorePackageMetadata? {
        val wrapper = json.parseToJsonElement(payload) as? JsonObject ?: return null
        val result = wrapper[packageId.toString()] as? JsonObject ?: return null
        val data = result["data"] as? JsonObject ?: return null
        return SteamStorePackageMetadata(
            name = data.string("name").orEmpty().trim(),
            imageUrl = data.string("page_image")
                ?: data.string("small_logo").orEmpty()
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
}

internal class SteamStorePackageMetadataService(
    private val client: OkHttpClient
) {
    fun enrich(
        options: List<SteamStorePackageOption>,
        countryCode: String?,
        language: String
    ): List<SteamStorePackageOption> = options.map { option ->
        val metadata = runCatching {
            val request = buildSteamStoreRequest(
                path = "/api/packagedetails",
                query = mapOf("packageids" to option.packageId.toString(), "l" to language),
                steamLoginSecure = null,
                countryCode = countryCode
            )
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                SteamStorePackageMetadataParser.parse(
                    packageId = option.packageId,
                    payload = response.body?.string().orEmpty()
                )
            }
        }.onFailure { error ->
            SteamDiagLogger.append(
                "store_package metadata_failed package_id=${option.packageId} " +
                    "type=${error.javaClass.simpleName}"
            )
        }.getOrNull()
        if (metadata == null) {
            option
        } else {
            option.copy(
                title = metadata.name.ifBlank { option.title },
                imageUrl = metadata.imageUrl
            )
        }
    }
}
