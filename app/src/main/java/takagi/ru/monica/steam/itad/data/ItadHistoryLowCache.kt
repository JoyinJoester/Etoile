package takagi.ru.monica.steam.itad.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.itad.domain.ItadHistoricalLow

internal data class ItadCachedValue<T>(
    val value: T,
    val expiresAtMillis: Long
) {
    fun isFresh(nowMillis: Long): Boolean = nowMillis < expiresAtMillis
}

internal interface ItadCacheGateway {
    fun readGameId(appId: Int): ItadCachedValue<String>?
    fun writeGameId(appId: Int, gameId: String, expiresAtMillis: Long)
    fun readGameUrl(gameId: String): ItadCachedValue<String>?
    fun writeGameUrl(gameId: String, url: String, expiresAtMillis: Long)
    fun readHistoryLow(appId: Int, countryCode: String): ItadCachedValue<ItadHistoricalLow>?
    fun writeHistoryLow(
        appId: Int,
        countryCode: String,
        value: ItadHistoricalLow,
        expiresAtMillis: Long
    )
    fun readRetryAfter(keyFingerprint: String): Long?
    fun writeRetryAfter(keyFingerprint: String, retryAfterEpochMillis: Long)
}

internal class ItadHistoryLowCache(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ItadCacheGateway {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun readGameId(appId: Int): ItadCachedValue<String>? =
        readEntry<GameIdCacheEntry>(gameIdKey(appId))?.let {
            ItadCachedValue(it.gameId, it.expiresAtMillis)
        }

    override fun writeGameId(appId: Int, gameId: String, expiresAtMillis: Long) {
        writeEntry(gameIdKey(appId), GameIdCacheEntry(gameId, expiresAtMillis))
    }

    override fun readGameUrl(gameId: String): ItadCachedValue<String>? =
        readEntry<GameUrlCacheEntry>(gameUrlKey(gameId))?.let {
            ItadCachedValue(it.url, it.expiresAtMillis)
        }

    override fun writeGameUrl(gameId: String, url: String, expiresAtMillis: Long) {
        writeEntry(gameUrlKey(gameId), GameUrlCacheEntry(url, expiresAtMillis))
    }

    override fun readHistoryLow(
        appId: Int,
        countryCode: String
    ): ItadCachedValue<ItadHistoricalLow>? =
        readEntry<HistoryLowCacheEntry>(historyKey(appId, countryCode))?.let {
            ItadCachedValue(it.value, it.expiresAtMillis)
        }

    override fun writeHistoryLow(
        appId: Int,
        countryCode: String,
        value: ItadHistoricalLow,
        expiresAtMillis: Long
    ) {
        writeEntry(
            historyKey(appId, countryCode),
            HistoryLowCacheEntry(value, expiresAtMillis)
        )
    }

    override fun readRetryAfter(keyFingerprint: String): Long? =
        preferences.getLong(retryKey(keyFingerprint), 0L).takeIf { it > 0L }

    override fun writeRetryAfter(keyFingerprint: String, retryAfterEpochMillis: Long) {
        preferences.edit()
            .putLong(retryKey(keyFingerprint), retryAfterEpochMillis)
            .apply()
    }

    private inline fun <reified T> readEntry(key: String): T? {
        val raw = preferences.getString(key, null) ?: return null
        return runCatching { json.decodeFromString<T>(raw) }
            .onFailure { preferences.edit().remove(key).apply() }
            .getOrNull()
    }

    private inline fun <reified T> writeEntry(key: String, value: T) {
        preferences.edit().putString(key, json.encodeToString(value)).apply()
    }

    private fun gameIdKey(appId: Int): String = "game_id_$appId"
    private fun gameUrlKey(gameId: String): String = "game_url_$gameId"
    private fun historyKey(appId: Int, countryCode: String): String =
        "history_${countryCode.uppercase()}_$appId"
    private fun retryKey(keyFingerprint: String): String = "retry_$keyFingerprint"

    @Serializable
    private data class GameIdCacheEntry(val gameId: String, val expiresAtMillis: Long)

    @Serializable
    private data class GameUrlCacheEntry(val url: String, val expiresAtMillis: Long)

    @Serializable
    private data class HistoryLowCacheEntry(
        val value: ItadHistoricalLow,
        val expiresAtMillis: Long
    )

    private companion object {
        const val PREFERENCES_NAME = "monica_itad_cache"
    }
}
