package takagi.ru.monica.steam.store.freebie.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieCatalog

internal class SteamFreebieCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "steam_freebie_cache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(accountId: Long?): SteamFreebieCatalog? = runCatching {
        val file = File(directory, cacheName(accountId))
        if (!file.isFile) return null
        json.decodeFromString<SteamFreebieCatalog>(file.readText())
    }.getOrNull()

    fun write(accountId: Long?, catalog: SteamFreebieCatalog) {
        runCatching {
            directory.mkdirs()
            val target = File(directory, cacheName(accountId))
            val pending = File(directory, "${target.name}.tmp")
            pending.writeText(json.encodeToString(catalog))
            if (!pending.renameTo(target)) {
                target.writeText(pending.readText())
                pending.delete()
            }
        }
    }

    private fun cacheName(accountId: Long?): String =
        accountId?.let { "account_${it}_freebies.json" } ?: "guest_freebies.json"
}
