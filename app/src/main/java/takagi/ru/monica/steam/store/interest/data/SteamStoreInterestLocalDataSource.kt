package takagi.ru.monica.steam.store.interest.data

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSnapshot

internal interface SteamStoreInterestLocalDataSource {
    fun load(steamId: String): SteamStoreInterestSnapshot
    fun save(steamId: String, snapshot: SteamStoreInterestSnapshot)
}

internal class SteamStoreInterestMemoryDataSource : SteamStoreInterestLocalDataSource {
    private val snapshots = ConcurrentHashMap<String, SteamStoreInterestSnapshot>()

    override fun load(steamId: String): SteamStoreInterestSnapshot =
        snapshots[steamId.trim()] ?: SteamStoreInterestSnapshot()

    override fun save(steamId: String, snapshot: SteamStoreInterestSnapshot) {
        snapshots[steamId.trim()] = snapshot
    }
}

internal class SteamStoreInterestPreferencesDataSource internal constructor(
    private val store: SteamStoreInterestKeyValueStore
) : SteamStoreInterestLocalDataSource {
    constructor(context: Context) : this(
        SteamStoreInterestSharedPreferencesStore(context.applicationContext)
    )

    override fun load(steamId: String): SteamStoreInterestSnapshot =
        store.get(key(steamId))
            ?.let(SteamStoreInterestSnapshotCodec::decode)
            ?: SteamStoreInterestSnapshot()

    override fun save(steamId: String, snapshot: SteamStoreInterestSnapshot) {
        store.put(key(steamId), SteamStoreInterestSnapshotCodec.encode(snapshot))
    }

    private fun key(steamId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(steamId.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "account_$digest"
    }
}

private object SteamStoreInterestSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: SteamStoreInterestSnapshot): String =
        json.encodeToString(SteamStoreInterestSnapshot.serializer(), snapshot)

    fun decode(raw: String): SteamStoreInterestSnapshot? = runCatching {
        json.decodeFromString(SteamStoreInterestSnapshot.serializer(), raw)
    }.getOrNull()
}
