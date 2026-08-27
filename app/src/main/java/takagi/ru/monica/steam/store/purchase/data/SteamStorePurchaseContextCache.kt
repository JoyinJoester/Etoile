package takagi.ru.monica.steam.store.purchase.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext

interface SteamStorePurchaseContextCache {
    fun load(accountSteamId: String, appId: Int): SteamStorePurchaseContext?
    fun save(context: SteamStorePurchaseContext)
}

internal interface SteamStorePurchaseKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SteamStorePurchasePreferencesCache internal constructor(
    private val store: SteamStorePurchaseKeyValueStore
) : SteamStorePurchaseContextCache {
    constructor(context: Context) : this(
        SteamStorePurchasePreferencesStore(context.applicationContext)
    )

    override fun load(accountSteamId: String, appId: Int): SteamStorePurchaseContext? =
        store.get(key(accountSteamId, appId))
            ?.let(SteamStorePurchaseContextCodec::decode)
            ?.takeIf { it.accountSteamId == accountSteamId && it.appId == appId }

    override fun save(context: SteamStorePurchaseContext) {
        store.put(
            key(context.accountSteamId, context.appId),
            SteamStorePurchaseContextCodec.encode(context)
        )
    }

    private fun key(accountSteamId: String, appId: Int): String {
        val value = "${accountSteamId.trim()}|$appId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "purchase_$digest"
    }
}

private class SteamStorePurchasePreferencesStore(
    context: Context
) : SteamStorePurchaseKeyValueStore {
    private val preferences = context.getSharedPreferences(
        "steam_store_purchase_context", Context.MODE_PRIVATE
    )

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

internal object SteamStorePurchaseContextCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(context: SteamStorePurchaseContext): String =
        json.encodeToString(SteamStorePurchaseContext.serializer(), context)

    fun decode(raw: String): SteamStorePurchaseContext? = runCatching {
        json.decodeFromString(SteamStorePurchaseContext.serializer(), raw)
    }.getOrNull()
}
