package takagi.ru.monica.steam.friends.chat.gameinvite.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.SteamChatGameInviteMetadata
import takagi.ru.monica.steam.store.data.SteamStoreCache
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

internal class SteamChatGameInviteMetadataRepository private constructor(
    context: Context,
    private val storeService: SteamStoreService = SteamStoreService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val storeCache = SteamStoreCache(context.applicationContext)
    private val memory = ConcurrentHashMap<String, SteamChatGameInviteMetadata>()
    private val loadMutex = Mutex()

    suspend fun resolve(appId: Int, language: String): SteamChatGameInviteMetadata? {
        if (appId <= 0) return null
        val key = "$language:$appId"
        memory[key]?.let { return it }
        return loadMutex.withLock {
            memory[key]?.let { return@withLock it }
            withContext(ioDispatcher) {
                val cached = storeCache.readDetail(accountId = null, appId = appId)
                    ?.takeIf { it.name.isNotBlank() }
                val detail = cached ?: runCatching {
                    storeService.compactDetail(appId = appId, language = language)
                }.onFailure { error ->
                    SteamDiagLogger.append(
                        "chat_game_invite metadata_failed app_id=$appId " +
                            "type=${error.javaClass.simpleName}"
                    )
                }.getOrNull()?.also { storeCache.writeDetail(accountId = null, detail = it) }
                detail?.toGameInviteMetadata()?.also { memory[key] = it }
            }
        }
    }

    private fun SteamStoreDetail.toGameInviteMetadata() = SteamChatGameInviteMetadata(
        appId = appId,
        name = name,
        headerImageUrl = headerImageUrl
    )

    companion object {
        @Volatile
        private var instance: SteamChatGameInviteMetadataRepository? = null

        fun get(context: Context): SteamChatGameInviteMetadataRepository =
            instance ?: synchronized(this) {
                instance ?: SteamChatGameInviteMetadataRepository(context.applicationContext)
                    .also { instance = it }
            }
    }
}
