package takagi.ru.monica.steam.store.related.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreRelatedApp(
    val appId: Int,
    val name: String,
    val headerImageUrl: String = ""
)
